package com.mufeng.api.marketing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单个商品项的营销分摊与出资方拆解明细 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemPromoSnapshotDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long skuId;

    private Long merchantId;

    private Integer buyQuantity;

    /**
     * 商品原总价（分）
     */
    private Long originTotalAmount;

    /**
     * 该商品分摊到的总优惠金额（分）：totalDiscountAmount = platformSubsidyAmount + merchantDiscountAmount
     */
    private Long totalDiscountAmount;

    /**
     * 其中：平台出资补贴金额（分，结算时需由平台补给商家）
     */
    private Long platformSubsidyAmount;

    /**
     * 其中：商家自主让利金额（分）
     */
    private Long merchantDiscountAmount;

    /**
     * 买家在该商品上的最终实付金额（分）：actualPayAmount = originTotalAmount - totalDiscountAmount
     */
    private Long actualPayAmount;
}