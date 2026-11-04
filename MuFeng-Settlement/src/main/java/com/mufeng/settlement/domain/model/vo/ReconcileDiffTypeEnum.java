package com.mufeng.settlement.domain.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * T+1 渠道对账差错类型枚举
 */
@Getter
@AllArgsConstructor
public enum ReconcileDiffTypeEnum {

    CROSS_DAY_BUFFER(0, "日切跨天缓冲挂起(23:55-00:05)"),
    LONG_BILL(1, "长款差错(渠道已收款，平台无已付记录)"),
    SHORT_BILL(2, "短款差错(平台记录已支付，渠道账单缺失)"),
    AMOUNT_MISMATCH(3, "金额不一致差错(平台实付金额与渠道入账金额不符)");

    private final Integer code;
    private final String desc;
}