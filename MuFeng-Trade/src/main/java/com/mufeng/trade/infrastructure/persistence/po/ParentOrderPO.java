package com.mufeng.trade.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 父订单分表持久化对象 (逻辑表名 t_parent_order，自动路由至 t_parent_order_00 ~ 15)
 */
@Data
@TableName("t_parent_order")
public class ParentOrderPO {

    @TableId(type = IdType.INPUT)
    private Long parentOrderNo;

    private Long userId;
    private Long totalOriginAmount;
    private Long totalDiscountAmount;
    private Long totalPayAmount;
    private Long usedCouponId;
    private Integer orderStatus;
    private Integer payChannel;
    private String channelTradeNo;
    private LocalDateTime payTime;
    private LocalDateTime expireTime;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}