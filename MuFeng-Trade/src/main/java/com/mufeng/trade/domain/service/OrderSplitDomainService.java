package com.mufeng.trade.domain.service;

import com.mufeng.api.marketing.dto.ItemPromoSnapshotDTO;
import com.mufeng.api.marketing.dto.PriceCalcRespDTO;
import com.mufeng.common.id.GeneSnowflakeIdGenerator;
import com.mufeng.trade.domain.model.aggregate.OrderAggregate;
import com.mufeng.trade.domain.model.entity.SubOrderEntity;
import com.mufeng.trade.domain.model.vo.ParentOrderStatusEnum;
import com.mufeng.trade.domain.model.vo.SubOrderStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 跨店父子订单自动拆分领域服务
 */
@Service
@RequiredArgsConstructor
public class OrderSplitDomainService {

    private final GeneSnowflakeIdGenerator idGenerator;

    /**
     * 构建父子订单聚合根（按 merchantId 自动拆分为多个店铺子订单，并汇总各店铺的平台补贴与商家让利）
     *
     * @param userId        买家用户ID
     * @param parentOrderNo 预生成的基因父订单号
     * @param priceCalcResp 营销服务锁券并分摊后的精确算价结果
     * @return 完整的订单聚合根
     */
    public OrderAggregate buildAndSplitOrder(Long userId, Long parentOrderNo, PriceCalcRespDTO priceCalcResp) {
        // 1. 按商家 ID (merchantId) 对商品分摊快照进行分组（保持原有顺序）
        Map<Long, List<ItemPromoSnapshotDTO>> merchantGroupMap = priceCalcResp.getItemSnapshots().stream()
                .collect(Collectors.groupingBy(
                        ItemPromoSnapshotDTO::getMerchantId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<SubOrderEntity> subOrders = new ArrayList<>(merchantGroupMap.size());

        // 2. 为每个商家生成独立的店铺子订单（子单号同样携带 userId 基因位）
        for (Map.Entry<Long, List<ItemPromoSnapshotDTO>> entry : merchantGroupMap.entrySet()) {
            Long merchantId = entry.getKey();
            List<ItemPromoSnapshotDTO> storeItems = entry.getValue();

            long subOrigin = 0L;
            long subDiscount = 0L;
            long subPlatformSubsidy = 0L;
            long subMerchantDiscount = 0L;
            long subPay = 0L;

            for (ItemPromoSnapshotDTO item : storeItems) {
                subOrigin += item.getOriginTotalAmount();
                subDiscount += item.getTotalDiscountAmount();
                subPlatformSubsidy += item.getPlatformSubsidyAmount();
                subMerchantDiscount += item.getMerchantDiscountAmount();
                subPay += item.getActualPayAmount();
            }

            SubOrderEntity subOrder = SubOrderEntity.builder()
                    .subOrderNo(idGenerator.nextOrderNo(userId))
                    .parentOrderNo(parentOrderNo)
                    .userId(userId)
                    .merchantId(merchantId)
                    .subOriginAmount(subOrigin)
                    .subDiscountAmount(subDiscount)
                    .platformSubsidyAmount(subPlatformSubsidy)
                    .merchantDiscountAmount(subMerchantDiscount)
                    .subPayAmount(subPay)
                    .refundedAmount(0L)
                    .orderStatus(SubOrderStatusEnum.WAIT_PAY.getCode())
                    .settleStatus(0)
                    .itemSnapshots(storeItems)
                    .build();

            subOrders.add(subOrder);
        }

        // 3. 组装顶层父订单聚合根（默认 15 分钟未支付自动过期）
        return OrderAggregate.builder()
                .parentOrderNo(parentOrderNo)
                .userId(userId)
                .totalOriginAmount(priceCalcResp.getTotalOriginAmount())
                .totalDiscountAmount(priceCalcResp.getTotalDiscountAmount())
                .totalPayAmount(priceCalcResp.getTotalPayAmount())
                .usedCouponId(priceCalcResp.getUsedUserCouponId())
                .orderStatus(ParentOrderStatusEnum.WAIT_PAY.getCode())
                .expireTime(LocalDateTime.now().plusMinutes(15))
                .subOrders(subOrders)
                .allItemSnapshots(priceCalcResp.getItemSnapshots())
                .build();
    }
}