package com.mufeng.common.lock;

import com.mufeng.common.core.ErrorCodeEnum;
import com.mufeng.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Redisson 声明式分布式锁 AOP 切面
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String dynamicKey = parseSpelKey(distributedLock.key(), joinPoint);
        String fullLockKey = distributedLock.prefix() + dynamicKey;

        RLock lock = redissonClient.getLock(fullLockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );
            if (!acquired) {
                log.warn("[分布式锁拦截] 获取锁失败, lockKey: {}", fullLockKey);
                throw new BizException(ErrorCodeEnum.LOCK_ACQUIRE_TIMEOUT, distributedLock.failMessage());
            }
            return joinPoint.proceed();
        } finally {
            // 仅当锁确实被当前线程持有时才执行释放，防止误释放其他线程的锁
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 解析方法上的 SpEL 表达式提取业务参数值
     */
    private String parseSpelKey(String spelExpression, ProceedingJoinPoint joinPoint) {
        if (spelExpression == null || !spelExpression.contains("#")) {
            return spelExpression;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] paramNames = nameDiscoverer.getParameterNames(method);
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        Expression expression = parser.parseExpression(spelExpression);
        Object value = expression.getValue(context);
        return value == null ? "null" : value.toString();
    }
}