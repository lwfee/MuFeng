package com.mufeng.api.marketing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 购物车/收银台待算价商品项 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "商品SKU ID不能为空")
    private Long skuId;

    @NotNull(message = "所属商家ID不能为空")
    private Long merchantId;

    /**
     * 商品名称（冗余用于快照展示）
     */
    private String skuName;

    @NotNull(message = "商品销售单价不能为空")
    @Min(value = 1, message = "商品销售单价必须大于0分")
    private Long unitPrice;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1件")
    private Integer buyQuantity;

    /**
     * 库存扣减模式：1-普通商品(走DB行锁)，2-秒杀爆品(走Redis划拨库存)
     */
    @NotNull(message = "库存扣减模式不能为空")
    private Integer deductMode;

    /**
     * 计算当前商品项原总价（单位：分）
     */
    public long calcOriginTotalAmount() {
        return this.unitPrice * this.buyQuantity;
    }
}