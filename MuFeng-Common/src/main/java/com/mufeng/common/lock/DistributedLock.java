package com.mufeng.common.lock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson 的声明式分布式锁注解（支持 SpEL 表达式动态提取业务参数）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁名称业务前缀（如 "lock:coupon:receive:"）
     */
    String prefix();

    /**
     * 支持 SpEL 表达式的动态锁 Key（如 "#userId" 或 "#req.parentOrderNo"）
     */
    String key();

    /**
     * 最大等待获取锁的时间（默认 0 秒，即抢不到锁立即快速失败，防线程堆积）
     */
    long waitTime() default 0L;

    /**
     * 锁持有超时时间（默认 -1，表示开启 Redisson 看门狗 WatchDog 自动续期）
     */
    long leaseTime() default -1L;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 获取锁失败时的自定义提示信息
     */
    String failMessage() default "请求正在处理中，请勿重复操作";
}