package com.kingman.companion.framework.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端编程式配置
 *
 * <p>通过 {@code @Value} 读取 {@code spring.data.redis.*}（Spring Boot 3.x 标准路径），
 * 绕开 Redisson Starter 的 YAML block 解析缺陷（block scalar 不支持 Spring EL 插值）。
 *
 * <p>本 Bean 一旦注册，Redisson Starter 的自动配置（{@code @ConditionalOnMissingBean}）不再生效。
 */
@Configuration
@ConditionalOnClass(RedissonClient.class)
public class RedissonConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.timeout:5000}")
    private String timeout;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig ssc = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(2)
                .setConnectTimeout(5000)
                .setTimeout(parseTimeoutMs(timeout));

        if (password != null && !password.isBlank()) {
            ssc.setPassword(password);
        }

        return Redisson.create(config);
    }

    /** 将 "5000ms" / "5000" 统一转为毫秒数 */
    private int parseTimeoutMs(String raw) {
        if (raw == null || raw.isBlank()) return 5000;
        String trimmed = raw.trim().toLowerCase();
        if (trimmed.endsWith("ms")) {
            return Integer.parseInt(trimmed.replace("ms", "").trim());
        }
        if (trimmed.endsWith("s")) {
            return Integer.parseInt(trimmed.replace("s", "").trim()) * 1000;
        }
        return Integer.parseInt(trimmed);
    }
}
