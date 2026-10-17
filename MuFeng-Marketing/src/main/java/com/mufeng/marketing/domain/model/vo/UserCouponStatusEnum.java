package com.mufeng.marketing.domain.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户优惠券状态枚举
 */
@Getter
@AllArgsConstructor
public enum UserCouponStatusEnum {

    UNUSED(1, "未使用"),
    LOCKED(2, "下单已锁定"),
    USED(3, "已核销消费"),
    EXPIRED(4, "已过期");

    private final Integer code;
    private final String desc;
}