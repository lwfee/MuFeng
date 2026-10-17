package com.mufeng.api.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单个商品 SKU 库存扣减明细 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuDeductItemDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "关联店铺子订单号不能为空")
    private Long subOrderNo;

    @NotNull(message = "商品SKU ID不能为空")
    private Long skuId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "扣减数量至少为1")
    private Integer deductQuantity;

    /**
     * 扣减模式：1-普通日常库存(MySQL行锁)，2-秒杀活动划拨库存(Caffeine售罄标记 + Redis Lua)
     */
    @NotNull(message = "库存扣减模式不能为空")
    private Integer deductMode;
}