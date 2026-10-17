package com.mufeng.api.marketing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 营销算价与分摊汇总响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalcRespDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 订单商品原总价汇总（分）
     */
    private Long totalOriginAmount;

    /**
     * 订单总优惠金额汇总（分）
     */
    private Long totalDiscountAmount;

    /**
     * 平台总补贴金额汇总（分）
     */
    private Long totalPlatformSubsidyAmount;

    /**
     * 商家总让利金额汇总（分）
     */
    private Long totalMerchantDiscountAmount;

    /**
     * 买家合并应支付总金额（分）
     */
    private Long totalPayAmount;

    /**
     * 本次实际生效的用户优惠券ID
     */
    private Long usedUserCouponId;

    /**
     * 各商品项的精确分摊快照列表
     */
    private List<ItemPromoSnapshotDTO> itemSnapshots;
}