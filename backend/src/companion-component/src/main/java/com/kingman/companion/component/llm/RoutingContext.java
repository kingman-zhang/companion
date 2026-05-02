package com.kingman.companion.component.llm;

import com.kingman.companion.component.safety.SafetyLevel;

/**
 * 路由上下文：携带路由决策所需的信号
 *
 * @param requestedTier 调用方期望的层级（Router 可能因其他信号上调）
 * @param inputLength   用户输入总字符数（含历史消息），用于长上下文判断
 * @param safetyLevel   安全检测级别
 */
public record RoutingContext(
        ModelTier requestedTier,
        int inputLength,
        SafetyLevel safetyLevel
) {
    // ── 工厂方法 ──────────────────────────────────────────────────────────────

    /** 普通聊天（默认 LITE，Router 可能按信号上调） */
    public static RoutingContext chat(int inputLength, SafetyLevel safetyLevel) {
        return new RoutingContext(ModelTier.LITE, inputLength, safetyLevel);
    }

    /** 深度分析请求 */
    public static RoutingContext deepAnalysis(int inputLength, SafetyLevel safetyLevel) {
        return new RoutingContext(ModelTier.ADVANCED, inputLength, safetyLevel);
    }

    /** 标准单轮任务（改写、评估解读） */
    public static RoutingContext standard() {
        return new RoutingContext(ModelTier.STANDARD, 0, SafetyLevel.SAFE);
    }
}
