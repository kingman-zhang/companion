package com.kingman.companion.component.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云百炼（DashScope）provider 级别配置（来自 {@code companion.llm.dashscope.*}）
 *
 * <p>使用 OpenAI 兼容接口，model-id、max-tokens、timeout 等 per-request 配置在
 * {@link RouterProperties} 的 chains 中按 tier 配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "companion.llm.dashscope")
public class DashScopeProperties {

    /** API Key，建议通过环境变量 {@code DASHSCOPE_API_KEY} 注入 */
    private String apiKey = "";

    /** 连接超时（秒） */
    private int connectTimeoutSeconds = 10;

    /** DashScope OpenAI 兼容接口根地址 */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
}
