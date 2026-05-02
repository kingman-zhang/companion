package com.kingman.companion.component.llm;

import com.kingman.companion.component.safety.SafetyLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 模型路由决策器
 *
 * <p>优先级从高到低：
 * <ol>
 *   <li>安全级别 CONCERNING → {@link ModelTier#SAFETY}（强制，不管 requestedTier）</li>
 *   <li>输入超过长上下文阈值 → {@link ModelTier#LONG_CONTEXT}（上调）</li>
 *   <li>使用调用方的 requestedTier（LITE / STANDARD / ADVANCED）</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class ModelRouter {

    private final RouterProperties properties;

    /**
     * 根据路由上下文返回最终的 {@link ModelTier}。
     */
    public ModelTier route(RoutingContext context) {
        // 1. 安全覆盖：令人担忧内容 → SAFETY 模型 + 安全策略
        if (context.safetyLevel() == SafetyLevel.CONCERNING) {
            return ModelTier.SAFETY;
        }

        // 2. 长上下文上调
        if (context.inputLength() > properties.getLongContextThreshold()) {
            return ModelTier.LONG_CONTEXT;
        }

        // 3. 调用方期望的层级
        return context.requestedTier();
    }
}
