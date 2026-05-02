package com.kingman.companion.component.llm;

import java.util.List;

/**
 * LLM 提供商接口（provider 无关）
 *
 * <p>每个实现类对应一个 AI 厂商（Anthropic / OpenAI / Google 等）。
 * {@link com.kingman.companion.component.llm.LlmGatewayImpl} 通过 {@link #supports(String)}
 * 选择合适的实现来执行调用。
 */
public interface LlmProvider {

    /**
     * 该 provider 是否能处理指定的 provider 标识。
     *
     * @param provider 小写 provider 名，如 {@code "anthropic"}
     */
    boolean supports(String provider);

    /**
     * 执行多轮对话（含历史消息）。
     *
     * @param systemPrompt 系统提示词
     * @param messages     消息列表（时间正序）
     * @param config       本次调用使用的模型配置（来自路由链）
     * @return 模型返回的文本内容
     * @throws Exception 任何网络、超时、API 错误；Gateway 负责捕获并 fallback
     */
    String call(String systemPrompt, List<LlmMessage> messages, ModelConfig config) throws Exception;
}
