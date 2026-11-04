package com.mufeng.trade.domain.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 店铺子订单状态机枚举（独立发货、独立收货、独立退款与清分）
 */
@Getter
@AllArgsConstructor
public enum SubOrderStatusEnum {

    WAIT_PAY(10, "待支付"),
    WAIT_DELIVER(20, "已支付待发货"),
    DELIVERED(30, "已发货待收货"),
    CONFIRMED(40, "已确认收货(触发清结算)"),
    COMPLETED(50, "交易完成"),
    CLOSED(60, "交易关闭(超时未付或已全额退款)");

    private final Integer code;
    private final String desc;
}