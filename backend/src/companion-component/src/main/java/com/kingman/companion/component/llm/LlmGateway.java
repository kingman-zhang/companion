package com.kingman.companion.component.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 调用统一入口（替代原 AnthropicClient）
 *
 * <p>调用方只需构建 {@link RoutingContext}，Gateway 负责：
 * <ol>
 *   <li>通过 {@link ModelRouter} 决定目标 {@link ModelTier}</li>
 *   <li>按配置的 fallback 链依次尝试各模型</li>
 *   <li>任一模型超时/失败时自动切换下一个</li>
 *   <li>安全层级为 {@code CONCERNING} 时自动注入安全回复策略</li>
 * </ol>
 */
public interface LlmGateway {

    /**
     * 多轮对话（含历史消息）
     *
     * @param systemPrompt 系统提示词
     * @param messages     对话历史（时间正序，最后一条为当前用户消息）
     * @param context      路由上下文
     * @return 模型返回的文本内容
     */
    String completeWithHistory(String systemPrompt, List<LlmMessage> messages, RoutingContext context);

    /**
     * 单轮对话快捷方法
     */
    default String complete(String systemPrompt, String userMessage, RoutingContext context) {
        return completeWithHistory(systemPrompt, List.of(LlmMessage.user(userMessage)), context);
    }

    /**
     * 流式多轮对话：逐 chunk 回调，在调用线程中同步执行。
     *
     * @param onChunk 每个文本 chunk 到来时的回调
     */
    void streamWithHistory(String systemPrompt, List<LlmMessage> messages, RoutingContext context,
                           Consumer<String> onChunk);
}
