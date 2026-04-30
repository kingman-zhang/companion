package com.kingman.companion.component.llm;

import java.util.List;

/**
 * Anthropic Claude 调用接口
 *
 * <p>核心方法为 {@link #completeWithHistory}（支持多轮对话上下文）。
 * 单轮快捷方法 {@link #complete} 以 default 方式实现，供改写等无状态场景使用。
 */
public interface AnthropicClient {

    /**
     * 多轮对话 Chat Completion
     *
     * @param systemPrompt 系统提示词
     * @param messages     对话历史（按时间正序，最后一条为当前用户消息）
     * @return Claude 返回的文本内容（content[0].text）
     */
    String completeWithHistory(String systemPrompt, List<AnthropicMessage> messages);

    /**
     * 单轮对话快捷方法（无历史上下文）
     */
    default String complete(String systemPrompt, String userMessage) {
        return completeWithHistory(systemPrompt,
                List.of(new AnthropicMessage("user", userMessage)));
    }
}
