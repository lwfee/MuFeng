package com.mufeng.marketing.domain.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 优惠券计算类型枚举
 */
@Getter
@AllArgsConstructor
public enum CouponTypeEnum {

    FULL_REDUCTION(1, "满减券"),
    DISCOUNT_RATE(2, "折扣券"),
    DIRECT_SUBSIDY(3, "无门槛直降津贴");

    private final Integer code;
    private final String desc;

    public static CouponTypeEnum of(Integer code) {
        for (CouponTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}