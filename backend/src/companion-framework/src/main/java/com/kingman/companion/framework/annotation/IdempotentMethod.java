package com.kingman.companion.framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 基于 Redisson 的分布式幂等锁，防重复提交
 * key 支持 Spring EL 表达式
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentMethod {

    /** 锁 key，支持 Spring EL 表达式（如 "#userId + ':createOrder'"） */
    String key();

    /** 锁过期时间（秒），默认 5 秒 */
    int expireTime() default 5;
}
