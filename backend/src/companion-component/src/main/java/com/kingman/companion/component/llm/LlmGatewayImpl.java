package com.kingman.companion.component.llm;

import com.kingman.companion.component.safety.SafetyLevel;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM Gateway 实现：路由 + Provider 分发 + Fallback
 *
 * <p>调用流程：
 * <ol>
 *   <li>{@link ModelRouter#route(RoutingContext)} 决定最终 {@link ModelTier}</li>
 *   <li>SAFETY tier 时，在 systemPrompt 前注入安全回复策略</li>
 *   <li>按 {@link RouterProperties#getChain(ModelTier)} 顺序依次尝试各 {@link ModelConfig}</li>
 *   <li>找到能 {@link LlmProvider#supports} 的 Provider 并调用</li>
 *   <li>抛出异常时记录 warn 并尝试下一个模型（fallback）</li>
 *   <li>链条耗尽仍失败 → 抛 {@link ApiException}({@link CodeEnum#AI_SERVICE_UNAVAILABLE})</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmGatewayImpl implements LlmGateway {

    /**
     * SAFETY tier 注入的系统提示前缀（不替换原 prompt，作为优先指令追加在前）
     */
    static final String SAFETY_SYSTEM_PREFIX = """
            【安全注意 - 最高优先级指令】
            用户当前可能处于情绪低落或心理危机状态。请严格遵守以下原则：
            1. 先表达真诚的关切和理解，不评判、不质疑
            2. 不提供任何关于自伤或伤害他人的方法或信息
            3. 如用户表达被动性消极想法（如"想消失"），温和共情并引导寻求现实支持
            4. 在回复末尾（仅当对话内容涉及情绪危机时）温和告知可拨打：
               心理援助热线：北京 010-82951332 / 全国 400-161-9995
            5. 保持语气温暖、平静，避免一切可能加剧对方焦虑的表达
            ───────────────────────────────────
            """;

    private final ModelRouter router;
    private final RouterProperties properties;
    private final List<LlmProvider> providers;

    @Override
    public String completeWithHistory(String systemPrompt,
                                      List<LlmMessage> messages,
                                      RoutingContext context) {
        ModelTier tier = router.route(context);
        List<ModelConfig> chain = properties.getChain(tier);

        if (chain.isEmpty()) {
            log.error("路由配置缺失，tier={} 没有可用模型链", tier);
            throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
        }

        // SAFETY tier：在系统提示前注入安全策略
        String effectiveSystemPrompt = (tier == ModelTier.SAFETY)
                ? SAFETY_SYSTEM_PREFIX + systemPrompt
                : systemPrompt;

        // 取最后一条用户消息用于日志
        String lastUserMsg = messages.isEmpty() ? ""
                : messages.get(messages.size() - 1).content();

        log.debug("[AI] system prompt: {}", effectiveSystemPrompt);
        log.info("[AI] 用户问题: {}", truncate(lastUserMsg, 500));

        long start = System.currentTimeMillis();

        for (ModelConfig config : chain) {
            LlmProvider provider = findProvider(config.provider());
            if (provider == null) {
                log.warn("未找到 provider 实现，跳过: provider={}, model={}", config.provider(), config.modelId());
                continue;
            }

            try {
                log.info("[AI] 调用: tier={}, provider={}, model={}, 历史消息={}条",
                        tier, config.provider(), config.modelId(), messages.size());
                String response = provider.call(effectiveSystemPrompt, messages, config);
                long elapsed = System.currentTimeMillis() - start;
                log.info("[AI] 回复 (耗时={}ms, model={}): {}", elapsed, config.modelId(), truncate(response, 500));
                return response;
            } catch (Exception e) {
                log.warn("模型调用失败，尝试下一个: model={}, reason={}", config.modelId(), e.getMessage());
            }
        }

        log.error("所有模型均失败，tier={}", tier);
        throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
    }

    @Override
    public void streamWithHistory(String systemPrompt, List<LlmMessage> messages,
                                  RoutingContext context, Consumer<String> onChunk) {
        ModelTier tier = router.route(context);
        List<ModelConfig> chain = properties.getChain(tier);

        if (chain.isEmpty()) {
            log.error("路由配置缺失，tier={} 没有可用模型链", tier);
            throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
        }

        String effectiveSystemPrompt = (tier == ModelTier.SAFETY)
                ? SAFETY_SYSTEM_PREFIX + systemPrompt
                : systemPrompt;

        String lastUserMsg = messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
        log.info("[AI-Stream] 用户问题: {}", truncate(lastUserMsg, 500));
        long start = System.currentTimeMillis();

        for (ModelConfig config : chain) {
            LlmProvider provider = findProvider(config.provider());
            if (provider == null) {
                log.warn("未找到 provider，跳过: provider={}", config.provider());
                continue;
            }
            try {
                log.info("[AI-Stream] 调用: tier={}, provider={}, model={}", tier, config.provider(), config.modelId());
                provider.callStream(effectiveSystemPrompt, messages, config, onChunk);
                log.info("[AI-Stream] 完成 (耗时={}ms, model={})", System.currentTimeMillis() - start, config.modelId());
                return;
            } catch (Exception e) {
                log.warn("流式模型失败，尝试下一个: model={}, reason={}", config.modelId(), e.getMessage());
            }
        }

        log.error("所有模型均失败，tier={}", tier);
        throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
    }

    private LlmProvider findProvider(String providerName) {
        return providers.stream()
                .filter(p -> p.supports(providerName))
                .findFirst()
                .orElse(null);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
