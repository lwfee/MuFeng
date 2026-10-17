package com.mufeng.api.inventory.dto;

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
 * 订单级批量预扣库存请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDeductReqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "父订单号不能为空")
    private Long parentOrderNo;

    @NotNull(message = "买家用户ID不能为空")
    private Long userId;

    @Valid
    @NotEmpty(message = "待扣减SKU明细列表不能为空")
    private List<SkuDeductItemDTO> deductItems;
}