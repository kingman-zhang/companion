package com.kingman.companion.framework.config;

import com.kingman.companion.framework.security.AuthContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.util.Optional;

/**
 * MongoDB 审计配置
 * 自动填充 createUser / modifyUser 字段
 */
@Configuration
@EnableMongoAuditing(auditorAwareRef = "auditorProvider")
public class MongoAuditConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.ofNullable(AuthContext.getCurrentUserId())
                .or(() -> Optional.of("system"));
    }
}
