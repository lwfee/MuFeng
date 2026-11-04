package com.mufeng.trade.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mufeng.api.marketing.dto.ItemPromoSnapshotDTO;
import com.mufeng.common.core.ErrorCodeEnum;
import com.mufeng.common.exception.BizException;
import com.mufeng.common.id.GeneSnowflakeIdGenerator;
import com.mufeng.trade.domain.model.aggregate.OrderAggregate;
import com.mufeng.trade.domain.model.entity.SubOrderEntity;
import com.mufeng.trade.domain.model.vo.SubOrderStatusEnum;
import com.mufeng.trade.domain.repository.TradeOrderRepository;
import com.mufeng.trade.infrastructure.config.GeneTablePreciseShardingAlgorithm;
import com.mufeng.trade.infrastructure.persistence.mapper.OrderItemPromoSnapshotMapper;
import com.mufeng.trade.infrastructure.persistence.mapper.ParentOrderMapper;
import com.mufeng.trade.infrastructure.persistence.mapper.SubOrderMapper;
import com.mufeng.trade.infrastructure.persistence.po.OrderItemPromoSnapshotPO;
import com.mufeng.trade.infrastructure.persistence.po.ParentOrderPO;
import com.mufeng.trade.infrastructure.persistence.po.SubOrderPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 交易订单领域仓储实现类（封装 16 分表基因路由与父子单持久化）
 */
@Repository
@RequiredArgsConstructor
public class TradeOrderRepositoryImpl implements TradeOrderRepository {

    private final ParentOrderMapper parentOrderMapper;
    private final SubOrderMapper subOrderMapper;
    private final OrderItemPromoSnapshotMapper snapshotMapper;
    private final GeneSnowflakeIdGenerator idGenerator;

    @Override
    public void saveOrderAggregate(OrderAggregate aggregate) {
        GeneTablePreciseShardingAlgorithm.bindShardKey(aggregate.getUserId());
        try {
            // 1. 落库父订单分表 (t_parent_order_xx)
            ParentOrderPO parentPO = new ParentOrderPO();
            parentPO.setParentOrderNo(aggregate.getParentOrderNo());
            parentPO.setUserId(aggregate.getUserId());
            parentPO.setTotalOriginAmount(aggregate.getTotalOriginAmount());
            parentPO.setTotalDiscountAmount(aggregate.getTotalDiscountAmount());
            parentPO.setTotalPayAmount(aggregate.getTotalPayAmount());
            parentPO.setUsedCouponId(aggregate.getUsedCouponId());
            parentPO.setOrderStatus(aggregate.getOrderStatus());
            parentPO.setExpireTime(aggregate.getExpireTime());
            parentPO.setVersion(0);
            parentOrderMapper.insert(parentPO);

            // 2. 遍历落库各店铺子订单分表 (t_sub_order_xx) 与商品分摊快照表
            for (SubOrderEntity sub : aggregate.getSubOrders()) {
                SubOrderPO subPO = new SubOrderPO();
                subPO.setSubOrderNo(sub.getSubOrderNo());
                subPO.setParentOrderNo(sub.getParentOrderNo());
                subPO.setUserId(sub.getUserId());
                subPO.setMerchantId(sub.getMerchantId());
                subPO.setSubOriginAmount(sub.getSubOriginAmount());
                subPO.setSubDiscountAmount(sub.getSubDiscountAmount());
                subPO.setPlatformSubsidyAmount(sub.getPlatformSubsidyAmount());
                subPO.setMerchantDiscountAmount(sub.getMerchantDiscountAmount());
                subPO.setSubPayAmount(sub.getSubPayAmount());
                subPO.setRefundedAmount(0L);
                subPO.setOrderStatus(sub.getOrderStatus());
                subPO.setSettleStatus(0);
                subPO.setVersion(0);
                subOrderMapper.insert(subPO);

                for (ItemPromoSnapshotDTO item : sub.getItemSnapshots()) {
                    OrderItemPromoSnapshotPO snapPO = new OrderItemPromoSnapshotPO();
                    snapPO.setId(idGenerator.nextId());
                    snapPO.setParentOrderNo(aggregate.getParentOrderNo());
                    snapPO.setSubOrderNo(sub.getSubOrderNo());
                    snapPO.setUserId(aggregate.getUserId());
                    snapPO.setMerchantId(sub.getMerchantId());
                    snapPO.setSkuId(item.getSkuId());
                    snapPO.setBuyQuantity(item.getBuyQuantity());
                    snapPO.setOriginTotalAmount(item.getOriginTotalAmount());
                    snapPO.setTotalDiscountAmount(item.getTotalDiscountAmount());
                    snapPO.setPlatformSubsidyAmount(item.getPlatformSubsidyAmount());
                    snapPO.setMerchantDiscountAmount(item.getMerchantDiscountAmount());
                    snapPO.setActualPayAmount(item.getActualPayAmount());
                    snapPO.setRefundStatus(0);
                    snapshotMapper.insert(snapPO);
                }
            }
        } finally {
            GeneTablePreciseShardingAlgorithm.clearShardKey();
        }
    }

    @Override
    public OrderAggregate getByParentOrderNo(Long parentOrderNo) {
        // 直接从 parentOrderNo 末4位提取基因路由分表，无需调用方传 userId！
        GeneTablePreciseShardingAlgorithm.bindShardKey(parentOrderNo);
        try {
            ParentOrderPO parentPO = parentOrderMapper.selectById(parentOrderNo);
            if (parentPO == null) {
                return null;
            }
            List<SubOrderPO> subPOList = subOrderMapper.selectList(
                    new LambdaQueryWrapper<SubOrderPO>().eq(SubOrderPO::getParentOrderNo, parentOrderNo)
            );
            List<SubOrderEntity> subEntities = subPOList.stream()
                    .map(this::toSubOrderEntity)
                    .collect(Collectors.toList());

            return OrderAggregate.builder()
                    .parentOrderNo(parentPO.getParentOrderNo())
                    .userId(parentPO.getUserId())
                    .totalOriginAmount(parentPO.getTotalOriginAmount())
                    .totalDiscountAmount(parentPO.getTotalDiscountAmount())
                    .totalPayAmount(parentPO.getTotalPayAmount())
                    .usedCouponId(parentPO.getUsedCouponId())
                    .orderStatus(parentPO.getOrderStatus())
                    .payChannel(parentPO.getPayChannel())
                    .channelTradeNo(parentPO.getChannelTradeNo())
                    .payTime(parentPO.getPayTime())
                    .expireTime(parentPO.getExpireTime())
                    .subOrders(subEntities)
                    .build();
        } finally {
            GeneTablePreciseShardingAlgorithm.clearShardKey();
        }
    }

    @Override
    public SubOrderEntity getSubOrderByNo(Long subOrderNo) {
        GeneTablePreciseShardingAlgorithm.bindShardKey(subOrderNo);
        try {
            SubOrderPO po = subOrderMapper.selectById(subOrderNo);
            return po == null ? null : toSubOrderEntity(po);
        } finally {
            GeneTablePreciseShardingAlgorithm.clearShardKey();
        }
    }

    @Override
    public boolean markOrderPaid(Long parentOrderNo, Integer payChannel, String channelTradeNo) {
        GeneTablePreciseShardingAlgorithm.bindShardKey(parentOrderNo);
        try {
            int updated = parentOrderMapper.updatePaidCas(parentOrderNo, payChannel, channelTradeNo);
            if (updated > 0) {
                subOrderMapper.activateSubOrdersOnPaid(parentOrderNo);
                return true;
            }
            return false;
        } finally {
            GeneTablePreciseShardingAlgorithm.clearShardKey();
        }
    }

    @Override
    public boolean closeUnpaidOrder(Long parentOrderNo) {
        GeneTablePreciseShardingAlgorithm.bindShardKey(parentOrderNo);
        try {
            int updated = parentOrderMapper.closeUnpaidParentOrderCas(parentOrderNo);
            if (updated > 0) {
                subOrderMapper.closeSubOrdersOnTimeout(parentOrderNo);
                return true;
            }
            return false;
        } finally {
            GeneTablePreciseShardingAlgorithm.clearShardKey();
        }
    }

    @Override
    public boolean deliverSubOrder(Long subOrderNo) {
        GeneTablePreciseShardingAlgorithm.bindShardKey(subOrderNo);
        try {
            return subOrderMapper.deliverSubOrderCas(subOrderNo) > 0;
        } finally {
            GeneTablePreciseShardingAlgorithm.clearShardKey();
        }
    }

    @Override
    public boolean confirmSubOrder(Long subOrderNo) {
        GeneTablePreciseShardingAlgorithm.bindShardKey(subOrderNo);
        try {
            return subOrderMapper.confirmSubOrderCas(subOrderNo) > 0;
        } finally {
            GeneTablePreciseShardingAlgorithm.clearShardKey();
        }
    }

    @Override
    public ItemPromoSnapshotDTO getItemSnapshot(Long subOrderNo, Long skuId) {
        OrderItemPromoSnapshotPO po = snapshotMapper.selectOne(
                new LambdaQueryWrapper<OrderItemPromoSnapshotPO>()
                        .eq(OrderItemPromoSnapshotPO::getSubOrderNo, subOrderNo)
                        .eq(OrderItemPromoSnapshotPO::getSkuId, skuId)
        );
        if (po == null) {
            return null;
        }
        return ItemPromoSnapshotDTO.builder()
                .skuId(po.getSkuId())
                .merchantId(po.getMerchantId())
                .buyQuantity(po.getBuyQuantity())
                .originTotalAmount(po.getOriginTotalAmount())
                .totalDiscountAmount(po.getTotalDiscountAmount())
                .platformSubsidyAmount(po.getPlatformSubsidyAmount())
                .merchantDiscountAmount(po.getMerchantDiscountAmount())
                .actualPayAmount(po.getActualPayAmount())
                .build();
    }

    @Override
    public boolean applySubOrderItemRefund(SubOrderEntity subOrder, Long skuId, long refundAmount) {
        // 1. CAS 更新商品项快照为已退款（防同一商品重复退）
        int snapUpdated = snapshotMapper.markItemRefundedCas(subOrder.getSubOrderNo(), skuId);
        if (snapUpdated <= 0) {
            throw new BizException(ErrorCodeEnum.ORDER_STATUS_ILLEGAL, "该商品项已申请过退款，请勿重复操作");
        }

        GeneTablePreciseShardingAlgorithm.bindShardKey(subOrder.getSubOrderNo());
        try {
            // 2. CAS 累加子订单已退金额并更新状态
            int subUpdated = subOrderMapper.updateSubOrderRefundCas(
                    subOrder.getSubOrderNo(), refundAmount, subOrder.getOrderStatus());
            if (subUpdated <= 0) {
                throw new BizException(ErrorCodeEnum.REFUND_AMOUNT_OVERFLOW);
            }

            // 3. 检查同一父单下是否所有子订单均已全额退完关闭 (CLOSED = 60)
            List<SubOrderPO> siblingSubs = subOrderMapper.selectList(
                    new LambdaQueryWrapper<SubOrderPO>()
                            .eq(SubOrderPO::getParentOrderNo, subOrder.getParentOrderNo())
            );
            boolean allSubOrdersClosed = siblingSubs.stream()
                    .allMatch(s -> SubOrderStatusEnum.CLOSED.getCode().equals(s.getOrderStatus()));

            if (allSubOrdersClosed) {
                parentOrderMapper.markAllCancelledCas(subOrder.getParentOrderNo());
            }
            return allSubOrdersClosed;
        } finally {
            GeneTablePreciseShardingAlgorithm.clearShardKey();
        }
    }

    @Override
    public List<SubOrderEntity> listUserSubOrders(Long userId) {
        GeneTablePreciseShardingAlgorithm.bindShardKey(userId);
        try {
            List<SubOrderPO> list = subOrderMapper.selectList(
                    new LambdaQueryWrapper<SubOrderPO>()
                            .eq(SubOrderPO::getUserId, userId)
                            .orderByDesc(SubOrderPO::getCreatedAt)
            );
            return list.stream().map(this::toSubOrderEntity).collect(Collectors.toList());
        } finally {
            GeneTablePreciseShardingAlgorithm.clearShardKey();
        }
    }

    private SubOrderEntity toSubOrderEntity(SubOrderPO po) {
        return SubOrderEntity.builder()
                .subOrderNo(po.getSubOrderNo())
                .parentOrderNo(po.getParentOrderNo())
                .userId(po.getUserId())
                .merchantId(po.getMerchantId())
                .subOriginAmount(po.getSubOriginAmount())
                .subDiscountAmount(po.getSubDiscountAmount())
                .platformSubsidyAmount(po.getPlatformSubsidyAmount())
                .merchantDiscountAmount(po.getMerchantDiscountAmount())
                .subPayAmount(po.getSubPayAmount())
                .refundedAmount(po.getRefundedAmount())
                .orderStatus(po.getOrderStatus())
                .settleStatus(po.getSettleStatus())
                .deliverTime(po.getDeliverTime())
                .confirmTime(po.getConfirmTime())
                .build();
    }
}