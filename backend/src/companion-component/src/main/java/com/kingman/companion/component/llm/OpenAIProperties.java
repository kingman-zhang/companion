package com.kingman.companion.component.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenAI provider 级别配置（来自 {@code companion.llm.openai.*}）。
 *
 * <p>model-id、max-tokens、timeout 等 per-request 配置在 {@link RouterProperties}
 * 的 chains 中按 tier 配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "companion.llm.openai")
public class OpenAIProperties {

    /** API Key，通过环境变量 {@code OPENAI_API_KEY} 注入 */
    private String apiKey = "";

    /** 连接超时（秒） */
    private int connectTimeoutSeconds = 10;

    /** OpenAI API 根地址 */
    private String baseUrl = "https://api.openai.com/v1";
}
