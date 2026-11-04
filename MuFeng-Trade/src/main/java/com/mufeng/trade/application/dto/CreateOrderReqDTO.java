package com.mufeng.trade.application.dto;

import com.mufeng.api.marketing.dto.CartItemDTO;
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
 * 买家收银台正式提单请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderReqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "买家用户ID不能为空")
    private Long userId;

    /**
     * 选用的优惠券实例ID（不传则按无优惠券原价建单）
     */
    private Long userCouponId;

    @Valid
    @NotEmpty(message = "购买商品明细列表不能为空")
    private List<CartItemDTO> items;
}