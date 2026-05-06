package com.kingman.companion.module.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 用户模块基础配置
 */
@Configuration
public class UserModuleConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
