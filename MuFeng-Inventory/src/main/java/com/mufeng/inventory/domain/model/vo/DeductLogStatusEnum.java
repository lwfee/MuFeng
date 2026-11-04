package com.mufeng.inventory.domain.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存扣减幂等流水状态枚举
 */
@Getter
@AllArgsConstructor
public enum DeductLogStatusEnum {

    LOCKED(1, "已预扣锁定"),
    CONFIRMED(2, "已支付实扣确认"),
    RELEASED(3, "已关单回滚释放");

    private final Integer code;
    private final String desc;
}