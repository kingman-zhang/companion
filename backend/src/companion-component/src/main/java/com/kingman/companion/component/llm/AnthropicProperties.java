package com.kingman.companion.component.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Anthropic Claude API 配置项
 * 对应 application.yml: companion.llm.anthropic.*
 */
@Data
@Component
@ConfigurationProperties(prefix = "companion.llm.anthropic")
public class AnthropicProperties {

    /** API Key，通过环境变量 ANTHROPIC_API_KEY 注入 */
    private String apiKey = "";

    /** 模型 ID */
    private String model = "claude-sonnet-4-6";

    /** 单次请求最大 token 数 */
    private int maxTokens = 1024;

    /** 连接超时（秒） */
    private int connectTimeoutSeconds = 10;

    /** 读取超时（秒） */
    private int readTimeoutSeconds = 60;

    /** API 基础地址 */
    private String baseUrl = "https://api.anthropic.com";
}
