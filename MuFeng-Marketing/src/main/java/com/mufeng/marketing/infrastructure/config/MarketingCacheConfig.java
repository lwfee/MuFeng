package com.mufeng.marketing.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mufeng.marketing.domain.model.entity.CouponTemplateEntity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 营销服务 Caffeine 本地高速缓存配置（支撑 8000 QPS 购物车实时试算零 DB 查询）
 */
@Configuration
public class MarketingCacheConfig {

    @Bean
    public Cache<Long, CouponTemplateEntity> couponTemplateLocalCache() {
        return Caffeine.newBuilder()
                .initialCapacity(128)
                .maximumSize(2000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
}