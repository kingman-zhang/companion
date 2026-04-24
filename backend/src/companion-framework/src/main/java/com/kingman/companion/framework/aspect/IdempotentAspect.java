package com.kingman.companion.framework.aspect;

import com.kingman.companion.framework.annotation.IdempotentMethod;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 分布式幂等锁切面（依赖 Redisson）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnClass(RedissonClient.class)
public class IdempotentAspect {

    private static final String LOCK_PREFIX = "idempotent:";

    private final RedissonClient redissonClient;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(idempotentMethod)")
    public Object around(ProceedingJoinPoint joinPoint, IdempotentMethod idempotentMethod) throws Throwable {
        String lockKey = LOCK_PREFIX + resolveKey(joinPoint, idempotentMethod.key());
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = lock.tryLock(0, idempotentMethod.expireTime(), TimeUnit.SECONDS);
        if (!acquired) {
            log.warn("幂等锁获取失败，重复请求被拦截: key={}", lockKey);
            throw new ApiException(CodeEnum.INVALID_REQUEST, "请勿重复提交");
        }

        try {
            return joinPoint.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(), method, joinPoint.getArgs(), nameDiscoverer);
        return String.valueOf(parser.parseExpression(keyExpression).getValue(context));
    }
}
