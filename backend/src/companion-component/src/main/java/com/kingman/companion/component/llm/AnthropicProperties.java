package com.kingman.companion.component.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Anthropic provider 级别配置（来自 {@code companion.llm.anthropic.*}）
 *
 * <p>model-id、max-tokens、timeout 等 per-request 配置已移至 {@link RouterProperties}，
 * 此处只保留 provider 级别的连接参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "companion.llm.anthropic")
public class AnthropicProperties {

    /** API Key，通过环境变量 {@code ANTHROPIC_API_KEY} 注入 */
    private String apiKey = "";

    /** 连接超时（秒） */
    private int connectTimeoutSeconds = 10;

    /** Anthropic API 根地址 */
    private String baseUrl = "https://api.anthropic.com";
}
