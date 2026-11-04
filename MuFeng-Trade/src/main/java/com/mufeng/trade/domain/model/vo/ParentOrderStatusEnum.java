package com.mufeng.trade.domain.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 父订单状态枚举（主要用于收银台合并支付与全局优惠券绑定）
 */
@Getter
@AllArgsConstructor
public enum ParentOrderStatusEnum {

    WAIT_PAY(10, "待支付"),
    PAID(20, "已支付(已拆单激活子订单)"),
    TIMEOUT_CLOSED(30, "支付超时已关闭"),
    ALL_CANCELLED(40, "子订单已全部全额退货取消");

    private final Integer code;
    private final String desc;
}