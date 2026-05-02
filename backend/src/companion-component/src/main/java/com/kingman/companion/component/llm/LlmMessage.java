package com.kingman.companion.component.llm;

/**
 * 统一 LLM 消息格式（provider 无关）
 *
 * <p>role 值遵循 OpenAI/Anthropic 惯例：{@code "user"} 或 {@code "assistant"}。
 */
public record LlmMessage(String role, String content) {

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content);
    }
}
