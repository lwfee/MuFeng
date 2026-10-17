package com.mufeng.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 店铺子订单确认收货触发清结算分账领域事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubOrderSettledEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * RocketMQ 统一 Topic 名称常量
     */
    public static final String TOPIC = "MUFENG_TRADE_SUB_ORDER_SETTLED_TOPIC";

    /**
     * 店铺子订单号
     */
    private Long subOrderNo;

    /**
     * 关联父订单号
     */
    private Long parentOrderNo;

    /**
     * 买家用户ID
     */
    private Long userId;

    /**
     * 商家ID
     */
    private Long merchantId;

    /**
     * 买家实付净额（已扣除逆向部分退款后的实付金额，单位：分）
     */
    private Long userNetPayAmount;

    /**
     * 平台应补给商家的补贴金额（单位：分）
     */
    private Long platformSubsidyAmount;

    /**
     * 商家自主承担的让利金额（单位：分）
     */
    private Long merchantDiscountAmount;

    /**
     * 确认收货时间
     */
    private LocalDateTime confirmTime;
}