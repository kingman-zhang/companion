package com.kingman.companion.component.safety;

import com.kingman.companion.framework.exception.SafetyBlockedException;

/**
 * 安全检测结果值对象
 *
 * <p>调用方式：
 * <pre>
 *   SafetyResult result = safetyChecker.check(content);
 *   result.throwIfBlocked();              // BLOCKED 时抛 HTTP 451，其余无操作
 *   SafetyLevel level = result.level();   // 供路由决策使用
 * </pre>
 */
public record SafetyResult(SafetyLevel level, String triggerType) {

    /** 内容安全 */
    public static SafetyResult pass() {
        return new SafetyResult(SafetyLevel.SAFE, null);
    }

    /** 内容令人担忧，路由到 SAFETY 模型，不阻断 */
    public static SafetyResult concerning(String triggerType) {
        return new SafetyResult(SafetyLevel.CONCERNING, triggerType);
    }

    /** 明确危险内容，直接 HTTP 451 */
    public static SafetyResult block(String triggerType) {
        return new SafetyResult(SafetyLevel.BLOCKED, triggerType);
    }

    public boolean safe() {
        return level == SafetyLevel.SAFE;
    }

    /**
     * BLOCKED 时抛出 {@link SafetyBlockedException}（HTTP 451），其余无操作。
     */
    public void throwIfBlocked() {
        if (level == SafetyLevel.BLOCKED) {
            throw new SafetyBlockedException(triggerType);
        }
    }
}
