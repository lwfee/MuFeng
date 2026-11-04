package com.mufeng.trade.domain.model.aggregate;

import com.mufeng.api.marketing.dto.ItemPromoSnapshotDTO;
import com.mufeng.trade.domain.model.entity.SubOrderEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易订单聚合根（統筹 1 个父订单 + N 个按店铺拆分的子订单 + M 个商品级分摊快照）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAggregate {

    /**
     * 父订单号（内嵌 4 位 user_id 基因）
     */
    private Long parentOrderNo;
    private Long userId;

    private Long totalOriginAmount;
    private Long totalDiscountAmount;
    private Long totalPayAmount;

    /**
     * 关联锁定的用户优惠券ID（未用券则为 null）
     */
    private Long usedCouponId;

    private Integer orderStatus;
    private Integer payChannel;
    private String channelTradeNo;
    private LocalDateTime payTime;
    private LocalDateTime expireTime;

    /**
     * 拆分出的店铺子订单列表
     */
    private List<SubOrderEntity> subOrders;

    /**
     * 全量商品项营销出资分摊快照列表
     */
    private List<ItemPromoSnapshotDTO> allItemSnapshots;
}