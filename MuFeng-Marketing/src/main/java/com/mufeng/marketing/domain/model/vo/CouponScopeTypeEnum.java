package com.mufeng.marketing.domain.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 优惠券适用范围枚举
 */
@Getter
@AllArgsConstructor
public enum CouponScopeTypeEnum {

    ALL_PLATFORM(1, "全平台跨店通用"),
    MERCHANT_ONLY(2, "指定店铺专用");

    private final Integer code;
    private final String desc;

    public static CouponScopeTypeEnum of(Integer code) {
        for (CouponScopeTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return ALL_PLATFORM;
    }
}