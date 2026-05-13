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
     * 聊天系统提示词（非流式，JSON 输出格式），可在 application.yml 中覆盖。
     */
    private String systemPrompt = "";

    /**
     * 流式聊天系统提示词（文本 + ###METADATA### 分隔符格式），可在 application.yml 中覆盖。
     */
    private String streamSystemPrompt = "";
}
