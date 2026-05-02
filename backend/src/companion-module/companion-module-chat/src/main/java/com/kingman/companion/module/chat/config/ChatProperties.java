package com.kingman.companion.module.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 聊天模块配置（来自 {@code companion.chat.*}）
 */
@Data
@Component
@ConfigurationProperties(prefix = "companion.chat")
public class ChatProperties {

    /**
     * 聊天系统提示词，可在 application.yml 中覆盖，无需重新编译。
     */
    private String systemPrompt = "";
}
