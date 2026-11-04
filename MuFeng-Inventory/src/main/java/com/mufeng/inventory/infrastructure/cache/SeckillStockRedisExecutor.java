package com.mufeng.inventory.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mufeng.common.core.ErrorCodeEnum;
import com.mufeng.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀划拨库存三级漏斗执行器（Caffeine 本地售罄拦截 + Redis 单Key Lua 原子预扣与回补）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStockRedisExecutor {

    private static final String SECKILL_STOCK_KEY_PREFIX = "mufeng:seckill:stock:";
    private static final String SECKILL_USERS_KEY_PREFIX = "mufeng:seckill:users:";

    private final StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> deductScript;
    private DefaultRedisScript<Long> releaseScript;

    /**
     * JVM 本地售罄标记缓存（Key: skuId, Value: true 表示已售罄）
     * 设置 3 分钟自动过期兜底，防止多实例间极端情况下状态长期不一致
     */
    private final Cache<Long, Boolean> localSoldOutCache = Caffeine.newBuilder()
            .initialCapacity(64)
            .maximumSize(1000)
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .build();

    @PostConstruct
    public void initLuaScripts() {
        deductScript = new DefaultRedisScript<>();
        deductScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seckill_stock_deduct.lua")));
        deductScript.setResultType(Long.class);

        releaseScript = new DefaultRedisScript<>();
        releaseScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seckill_stock_release.lua")));
        releaseScript.setResultType(Long.class);
    }

    /**
     * 1. 秒杀库存划拨预热写入 Redis，并清除本地售罄标记
     */
    public void initOrIncrementSeckillStock(Long skuId, int allocateNum) {
        String stockKey = SECKILL_STOCK_KEY_PREFIX + skuId;
        stringRedisTemplate.opsForValue().increment(stockKey, allocateNum);
        localSoldOutCache.invalidate(skuId);
    }

    /**
     * 2. 执行秒杀库存预扣（先查本地售罄标记 -> 再执行 Redis Lua）
     */
    public void executeSeckillDeduct(Long skuId, Long userId, int deductNum) {
        // 第一层漏斗：JVM 本地内存售罄拦截（0.01ms 极速挡住 90% 溢出流量）
        if (Boolean.TRUE.equals(localSoldOutCache.getIfPresent(skuId))) {
            throw new BizException(ErrorCodeEnum.SECKILL_STOCK_SOLD_OUT);
        }

        // 第二层漏斗：Redis Lua 脚本原子校验限购与扣减库存
        List<String> keys = Arrays.asList(
                SECKILL_STOCK_KEY_PREFIX + skuId,
                SECKILL_USERS_KEY_PREFIX + skuId
        );
        Long result = stringRedisTemplate.execute(
                deductScript,
                keys,
                String.valueOf(userId),
                String.valueOf(deductNum)
        );

        if (result == null || result == -1L) {
            // 标记本地已售罄
            localSoldOutCache.put(skuId, Boolean.TRUE);
            throw new BizException(ErrorCodeEnum.SECKILL_STOCK_SOLD_OUT);
        }
        if (result == -2L) {
            throw new BizException(ErrorCodeEnum.SECKILL_REPEAT_BUY);
        }
        if (result == 0L) {
            // 刚好卖完最后一件，立即点亮本地售罄标记
            localSoldOutCache.put(skuId, Boolean.TRUE);
            log.info("[秒杀库存] SKU: {} 划拨库存已售罄，已激活本地内存拦截标记", skuId);
        }
    }

    /**
     * 3. 关单或异常回滚：释放 Redis 秒杀库存并移除用户已购标记，同时清除售罄标记
     */
    public void executeSeckillRelease(Long skuId, Long userId, int releaseNum) {
        List<String> keys = Arrays.asList(
                SECKILL_STOCK_KEY_PREFIX + skuId,
                SECKILL_USERS_KEY_PREFIX + skuId
        );
        Long released = stringRedisTemplate.execute(
                releaseScript,
                keys,
                String.valueOf(userId),
                String.valueOf(releaseNum)
        );
        if (released != null && released > 0) {
            localSoldOutCache.invalidate(skuId);
            log.info("[秒杀库存回补] SKU: {}, userId: {}, 回补数量: {}, 当前可用: {}", skuId, userId, releaseNum, released);
        }
    }

    /**
     * 4. 活动结束：原子提取并清空 Redis 中剩余未卖完的秒杀库存，用于回拨给 MySQL 主库存
     */
    public int extractRemainingStockForReclaim(Long skuId) {
        String stockKey = SECKILL_STOCK_KEY_PREFIX + skuId;
        String usersKey = SECKILL_USERS_KEY_PREFIX + skuId;
        String val = stringRedisTemplate.opsForValue().getAndDelete(stockKey);
        stringRedisTemplate.delete(usersKey);
        localSoldOutCache.invalidate(skuId);
        return val == null ? 0 : Math.max(0, Integer.parseInt(val));
    }
}