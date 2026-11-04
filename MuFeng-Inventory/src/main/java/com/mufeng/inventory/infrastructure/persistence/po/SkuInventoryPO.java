package com.mufeng.inventory.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品双模库存表持久化对象 (对应 t_sku_inventory)
 */
@Data
@TableName("t_sku_inventory")
public class SkuInventoryPO {

    @TableId(type = IdType.INPUT)
    private Long skuId;

    private Long merchantId;
    private String skuName;
    private Long salePrice;
    private Integer availableStock;
    private Integer lockedStock;
    private Integer activityFrozenStock;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}