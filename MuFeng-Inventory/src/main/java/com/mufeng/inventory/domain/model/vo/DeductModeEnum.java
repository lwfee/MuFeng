package com.mufeng.inventory.domain.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 双模库存扣减模式枚举
 */
@Getter
@AllArgsConstructor
public enum DeductModeEnum {

    NORMAL_DB_LOCK(1, "普通商品日常库存(走MySQL行锁)"),
    SECKILL_REDIS_LUA(2, "秒杀爆品划拨库存(走Caffeine售罄标记+Redis Lua)");

    private final Integer code;
    private final String desc;

    public static boolean isSeckill(Integer code) {
        return SECKILL_REDIS_LUA.code.equals(code);
    }
}