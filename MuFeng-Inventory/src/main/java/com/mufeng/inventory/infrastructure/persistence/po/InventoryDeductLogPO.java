package com.mufeng.inventory.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存扣减与释放幂等流水表持久化对象 (对应 t_inventory_deduct_log)
 */
@Data
@TableName("t_inventory_deduct_log")
public class InventoryDeductLogPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentOrderNo;
    private Long subOrderNo;
    private Long skuId;
    private Integer deductMode;
    private Integer deductNum;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}