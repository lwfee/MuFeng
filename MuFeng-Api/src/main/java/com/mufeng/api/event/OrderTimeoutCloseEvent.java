package com.mufeng.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单超时未支付自动关单领域事件（RocketMQ 定时/延迟消息载体）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutCloseEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * RocketMQ 统一 Topic 名称常量
     */
    public static final String TOPIC = "MUFENG_TRADE_ORDER_TIMEOUT_CLOSE_TOPIC";

    /**
     * 父订单号（内含 4位 user_id 分表基因）
     */
    private Long parentOrderNo;

    /**
     * 买家用户ID
     */
    private Long userId;

    /**
     * 锁定的优惠券实例ID（关单时需同步解锁）
     */
    private Long usedCouponId;

    /**
     * 事件创建时间
     */
    private LocalDateTime createTime;
}