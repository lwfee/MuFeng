package com.mufeng.inventory.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品双模库存领域实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuInventoryEntity {

    private Long skuId;
    private Long merchantId;
    private String skuName;
    /**
     * 销售单价（单位：分）
     */
    private Long salePrice;
    /**
     * 日常可用库存（普通商品下单直接扣减）
     */
    private Integer availableStock;
    /**
     * 日常下单锁定中库存
     */
    private Integer lockedStock;
    /**
     * 秒杀活动独立划拨冻结库存（与日常库存物理隔离）
     */
    private Integer activityFrozenStock;
}