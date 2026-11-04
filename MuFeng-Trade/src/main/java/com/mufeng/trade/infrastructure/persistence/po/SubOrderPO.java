package com.mufeng.trade.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺子订单分表持久化对象 (逻辑表名 t_sub_order，自动路由至 t_sub_order_00 ~ 15)
 */
@Data
@TableName("t_sub_order")
public class SubOrderPO {

    @TableId(type = IdType.INPUT)
    private Long subOrderNo;

    private Long parentOrderNo;
    private Long userId;
    private Long merchantId;
    private Long subOriginAmount;
    private Long subDiscountAmount;
    private Long platformSubsidyAmount;
    private Long merchantDiscountAmount;
    private Long subPayAmount;
    private Long refundedAmount;
    private Integer orderStatus;
    private Integer settleStatus;
    private LocalDateTime deliverTime;
    private LocalDateTime confirmTime;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}