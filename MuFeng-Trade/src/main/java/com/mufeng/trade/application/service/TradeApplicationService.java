package com.mufeng.trade.application.service;

import com.mufeng.api.event.OrderTimeoutCloseEvent;
import com.mufeng.api.event.SubOrderSettledEvent;
import com.mufeng.api.inventory.dto.InventoryDeductReqDTO;
import com.mufeng.api.inventory.dto.SkuDeductItemDTO;
import com.mufeng.api.inventory.facade.InventoryFeignClient;
import com.mufeng.api.marketing.dto.CartItemDTO;
import com.mufeng.api.marketing.dto.ItemPromoSnapshotDTO;
import com.mufeng.api.marketing.dto.PriceCalcReqDTO;
import com.mufeng.api.marketing.dto.PriceCalcRespDTO;
import com.mufeng.api.marketing.facade.MarketingFeignClient;
import com.mufeng.common.core.ErrorCodeEnum;
import com.mufeng.common.core.Result;
import com.mufeng.common.exception.BizException;
import com.mufeng.common.id.GeneSnowflakeIdGenerator;
import com.mufeng.common.lock.DistributedLock;
import com.mufeng.trade.application.dto.CreateOrderReqDTO;
import com.mufeng.trade.domain.model.aggregate.OrderAggregate;
import com.mufeng.trade.domain.model.entity.SubOrderEntity;
import com.mufeng.trade.domain.repository.TradeOrderRepository;
import com.mufeng.trade.domain.service.OrderSplitDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 交易履约核心应用编排服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeApplicationService {

    private final GeneSnowflakeIdGenerator idGenerator;
    private final OrderSplitDomainService orderSplitDomainService;
    private final TradeOrderRepository tradeOrderRepository;
    private final MarketingFeignClient marketingFeignClient;
    private final InventoryFeignClient inventoryFeignClient;
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 1. 收银台提单：锁券分摊 -> 父子拆单 -> 双模库存预扣 -> 16分表落盘 -> 投递延迟关单消息
     */
    @DistributedLock(
            prefix = "lock:trade:create:",
            key = "#req.userId",
            failMessage = "订单正在创建中，请勿重复提交"
    )
    @Transactional(rollbackFor = Exception.class)
    public OrderAggregate createOrder(CreateOrderReqDTO req) {
        // 1. 生成内嵌 4位 user_id 基因的父订单号
        Long parentOrderNo = idGenerator.nextOrderNo(req.getUserId());
        boolean couponLocked = false;
        boolean stockDeducted = false;

        try {
            // 2. 远程调用营销中心：锁定优惠券并执行最大余数法双层分摊计算
            PriceCalcReqDTO calcReq = PriceCalcReqDTO.builder()
                    .userId(req.getUserId())
                    .parentOrderNo(parentOrderNo)
                    .userCouponId(req.getUserCouponId())
                    .items(req.getItems())
                    .build();
            Result<PriceCalcRespDTO> calcResult = marketingFeignClient.lockCouponAndCalc(calcReq);
            if (calcResult == null || !calcResult.isSuccess() || calcResult.getData() == null) {
                throw new BizException(ErrorCodeEnum.RPC_INVOKE_ERROR,
                        calcResult != null ? calcResult.getMessage() : "营销算价锁券服务调用失败");
            }
            couponLocked = (calcResult.getData().getUsedUserCouponId() != null);

            // 3. 领域服务：按店铺 merchantId 自动拆分为父子订单聚合根
            OrderAggregate aggregate = orderSplitDomainService.buildAndSplitOrder(
                    req.getUserId(), parentOrderNo, calcResult.getData());

            // 4. 组装子单号与 SKU 扣减模式映射，远程调用库存中心执行双模库存预扣
            Map<Long, Integer> skuDeductModeMap = req.getItems().stream()
                    .collect(Collectors.toMap(CartItemDTO::getSkuId, CartItemDTO::getDeductMode, (a, b) -> a));

            List<SkuDeductItemDTO> deductItems = new ArrayList<>();
            for (SubOrderEntity subOrder : aggregate.getSubOrders()) {
                for (ItemPromoSnapshotDTO itemSnap : subOrder.getItemSnapshots()) {
                    deductItems.add(SkuDeductItemDTO.builder()
                            .subOrderNo(subOrder.getSubOrderNo())
                            .skuId(itemSnap.getSkuId())
                            .deductQuantity(itemSnap.getBuyQuantity())
                            .deductMode(skuDeductModeMap.getOrDefault(itemSnap.getSkuId(), 1))
                            .build());
                }
            }

            InventoryDeductReqDTO deductReq = InventoryDeductReqDTO.builder()
                    .parentOrderNo(parentOrderNo)
                    .userId(req.getUserId())
                    .deductItems(deductItems)
                    .build();
            Result<Void> deductResult = inventoryFeignClient.tryDeductStock(deductReq);
            if (deductResult == null || !deductResult.isSuccess()) {
                throw new BizException(ErrorCodeEnum.NORMAL_STOCK_INSUFFICIENT,
                        deductResult != null ? deductResult.getMessage() : "库存预扣失败");
            }
            stockDeducted = true;

            // 5. 本地事务落盘：按基因位写入父订单分表、子订单分表及商品分摊快照表
            tradeOrderRepository.saveOrderAggregate(aggregate);

            // 6. 发送 RocketMQ 延迟关单消息（DelayLevel = 16 对应 30分钟，或生产环境自定义15分钟定时级别）
            sendTimeoutCloseDelayMessage(aggregate);

            log.info("[交易中心] 跨店父子拆单提单成功, parentOrderNo: {}, 拆分店铺子单数: {}, 应支付总额: {}分",
                    parentOrderNo, aggregate.getSubOrders().size(), aggregate.getTotalPayAmount());
            return aggregate;

        } catch (Exception e) {
            log.error("[交易中心] 提单异常触发逆向补偿, parentOrderNo: {}", parentOrderNo, e);
            if (stockDeducted) {
                try {
                    inventoryFeignClient.releaseStock(parentOrderNo);
                } catch (Exception ex) {
                    log.error("[交易中心] 提单异常补偿释放库存失败, parentOrderNo: {}", parentOrderNo, ex);
                }
            }
            if (couponLocked) {
                try {
                    marketingFeignClient.releaseCoupon(req.getUserId(), parentOrderNo);
                } catch (Exception ex) {
                    log.error("[交易中心] 提单异常补偿解锁优惠券失败, parentOrderNo: {}", parentOrderNo, ex);
                }
            }
            throw e;
        }
    }

    /**
     * 2. 支付成功回调：激活所有店铺子订单为待发货(20)，并通知库存中心实扣确认
     */
    @DistributedLock(prefix = "lock:trade:parent:", key = "#parentOrderNo")
    @Transactional(rollbackFor = Exception.class)
    public void onPaySuccess(Long parentOrderNo, Integer payChannel, String channelTradeNo) {
        boolean updated = tradeOrderRepository.markOrderPaid(parentOrderNo, payChannel, channelTradeNo);
        if (!updated) {
            log.info("[交易中心] 订单已处理过支付或已关闭(幂等跳过), parentOrderNo: {}", parentOrderNo);
            return;
        }
        // 远程通知库存中心将预扣锁定库存转为实扣确认
        inventoryFeignClient.confirmDeductStock(parentOrderNo);
        log.info("[交易中心] 父订单支付成功并激活全部店铺子单, parentOrderNo: {}, channelTradeNo: {}",
                parentOrderNo, channelTradeNo);
    }

    /**
     * 3. 超时未支付自动关单（由 RocketMQ 延迟消息触发，具备 CAS 幂等保护）
     */
    @DistributedLock(prefix = "lock:trade:parent:", key = "#event.parentOrderNo")
    @Transactional(rollbackFor = Exception.class)
    public void closeTimeoutOrder(OrderTimeoutCloseEvent event) {
        boolean closed = tradeOrderRepository.closeUnpaidOrder(event.getParentOrderNo());
        if (!closed) {
            // 说明用户已完成支付或已手工取消，直接幂等返回
            return;
        }
        // 1. 远程释放双模库存
        inventoryFeignClient.releaseStock(event.getParentOrderNo());
        // 2. 若使用了优惠券，远程解锁归还给用户
        if (event.getUsedCouponId() != null) {
            marketingFeignClient.releaseCoupon(event.getUserId(), event.getParentOrderNo());
        }
        log.info("[交易中心] 超时未支付自动关单并回补库存/优惠券完成, parentOrderNo: {}", event.getParentOrderNo());
    }

    /**
     * 4. 商家针对单个店铺子订单独立发货
     */
    @Transactional(rollbackFor = Exception.class)
    public void deliverSubOrder(Long subOrderNo) {
        boolean delivered = tradeOrderRepository.deliverSubOrder(subOrderNo);
        if (!delivered) {
            throw new BizException(ErrorCodeEnum.ORDER_STATUS_ILLEGAL, "子订单当前状态无法执行发货");
        }
        log.info("[交易中心] 店铺子订单发货成功, subOrderNo: {}", subOrderNo);
    }

    /**
     * 5. 买家针对单个店铺子订单确认收货 -> 投递清结算分账领域事件
     */
    @DistributedLock(prefix = "lock:trade:suborder:", key = "#subOrderNo")
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceiveSubOrder(Long subOrderNo) {
        SubOrderEntity subOrder = tradeOrderRepository.getSubOrderByNo(subOrderNo);
        if (subOrder == null) {
            throw new BizException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        boolean confirmed = tradeOrderRepository.confirmSubOrder(subOrderNo);
        if (!confirmed) {
            throw new BizException(ErrorCodeEnum.ORDER_STATUS_ILLEGAL, "子订单当前状态无法确认收货");
        }

        // 构建清结算领域事件并投递至 RocketMQ，由 MuFeng-Settlement 异步生成商家结算单
        SubOrderSettledEvent settledEvent = SubOrderSettledEvent.builder()
                .subOrderNo(subOrder.getSubOrderNo())
                .parentOrderNo(subOrder.getParentOrderNo())
                .userId(subOrder.getUserId())
                .merchantId(subOrder.getMerchantId())
                .userNetPayAmount(subOrder.calcUserNetPayAmount())
                .platformSubsidyAmount(subOrder.getPlatformSubsidyAmount())
                .merchantDiscountAmount(subOrder.getMerchantDiscountAmount())
                .confirmTime(LocalDateTime.now())
                .build();

        try {
            rocketMQTemplate.convertAndSend(SubOrderSettledEvent.TOPIC, settledEvent);
        } catch (Exception e) {
            log.error("[交易中心] 发送子订单清结算 MQ 事件异常, subOrderNo: {}", subOrderNo, e);
            throw new BizException(ErrorCodeEnum.SYSTEM_ERROR, "触发清结算消息发送失败");
        }

        log.info("[交易中心] 子订单确认收货并触发清结算成功, subOrderNo: {}, 买家实付净额: {}分, 平台补贴: {}分",
                subOrderNo, settledEvent.getUserNetPayAmount(), settledEvent.getPlatformSubsidyAmount());
    }

    /**
     * 6. 逆向部分退货退款：读取建单时的正向最大余数法快照实付金额退回，若全单退完自动解券
     */
    @DistributedLock(prefix = "lock:trade:suborder:", key = "#subOrderNo")
    @Transactional(rollbackFor = Exception.class)
    public Long refundSubOrderItem(Long subOrderNo, Long skuId) {
        SubOrderEntity subOrder = tradeOrderRepository.getSubOrderByNo(subOrderNo);
        if (subOrder == null) {
            throw new BizException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        // 1. 读取建单时写死的该商品项分摊快照（正向建单即分摊，彻底消除退款时的尾差计算争议）
        ItemPromoSnapshotDTO itemSnap = tradeOrderRepository.getItemSnapshot(subOrderNo, skuId);
        if (itemSnap == null) {
            throw new BizException(ErrorCodeEnum.SKU_NOT_FOUND, "该子订单中不存在此商品项");
        }

        long refundAmount = itemSnap.getActualPayAmount();
        subOrder.validateAndApplyItemRefund(refundAmount);

        // 2. CAS 更新快照退款状态与子订单已退金额，并检测同一父单下是否所有子单均已全退
        boolean allParentItemsRefunded = tradeOrderRepository.applySubOrderItemRefund(subOrder, skuId, refundAmount);

        // 3. 若同一父单下所有店铺子单均已全额退完，自动解锁并归还用户的优惠券！
        if (allParentItemsRefunded) {
            marketingFeignClient.releaseCoupon(subOrder.getUserId(), subOrder.getParentOrderNo());
            log.info("[交易中心] 父订单下所有子订单已全部退完，已自动触发优惠券解锁归还, parentOrderNo: {}",
                    subOrder.getParentOrderNo());
        }

        log.info("[交易中心] 子订单单项退款成功, subOrderNo: {}, skuId: {}, 原路退回实付金额: {}分",
                subOrderNo, skuId, refundAmount);
        return refundAmount;
    }

    public OrderAggregate getOrderDetail(Long parentOrderNo) {
        return tradeOrderRepository.getByParentOrderNo(parentOrderNo);
    }

    public List<SubOrderEntity> listUserSubOrders(Long userId) {
        return tradeOrderRepository.listUserSubOrders(userId);
    }

    /**
     * 投递 RocketMQ 延迟关单消息（若本地调试未启动 Broker 则仅打印警告日志，不阻断主流程）
     */
    private void sendTimeoutCloseDelayMessage(OrderAggregate aggregate) {
        try {
            OrderTimeoutCloseEvent event = OrderTimeoutCloseEvent.builder()
                    .parentOrderNo(aggregate.getParentOrderNo())
                    .userId(aggregate.getUserId())
                    .usedCouponId(aggregate.getUsedCouponId())
                    .createTime(LocalDateTime.now())
                    .build();
            // RocketMQ delayLevel=16 代表延迟 30 分钟（Level=15 为 20 分钟，Level=14 为 10 分钟）
            rocketMQTemplate.syncSend(
                    OrderTimeoutCloseEvent.TOPIC,
                    MessageBuilder.withPayload(event).build(),
                    3000,
                    15
            );
        } catch (Exception e) {
            log.warn("[交易中心] 延迟关单消息投递提醒(请确认RocketMQ已启动), parentOrderNo: {}",
                    aggregate.getParentOrderNo(), e);
        }
    }
}