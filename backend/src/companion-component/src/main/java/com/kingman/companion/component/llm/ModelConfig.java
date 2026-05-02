package com.kingman.companion.component.llm;

/**
 * 单个模型配置（路由链条中的一个节点）
 *
 * <p>对应 {@code companion.llm.router.chains[TIER][n]} 的一条配置项。
 *
 * @param provider       提供商标识，小写，如 {@code "anthropic"}、{@code "openai"}、{@code "google"}
 * @param modelId        模型 ID，如 {@code "claude-haiku-4-5-20251001"}
 * @param maxTokens      最大输出 token 数
 * @param timeoutSeconds 单次请求超时（秒），超时后 Gateway 自动切下一个模型
 */
public record ModelConfig(
        String provider,
        String modelId,
        int maxTokens,
        int timeoutSeconds
) {}
