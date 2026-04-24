package com.kingman.companion.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * RelationshipAI 后端服务入口
 */
@SpringBootApplication(scanBasePackages = "com.kingman.companion")
@EnableMongoRepositories(basePackages = "com.kingman.companion")
public class CompanionApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompanionApplication.class, args);
    }
}
