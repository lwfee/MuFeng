package com.mufeng.api.marketing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 营销算价与下单锁券请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalcReqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "买家用户ID不能为空")
    private Long userId;

    /**
     * 父订单号（购物车试算时为空；正式提单锁券时必传）
     */
    private Long parentOrderNo;

    /**
     * 用户选择使用的优惠券实例ID（不传表示不使用优惠券或由系统推荐）
     */
    private Long userCouponId;

    @Valid
    @NotEmpty(message = "待算价商品列表不能为空")
    private List<CartItemDTO> items;
}