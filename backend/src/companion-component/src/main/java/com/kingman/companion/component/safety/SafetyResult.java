package com.kingman.companion.component.safety;

import com.kingman.companion.framework.exception.SafetyBlockedException;

/**
 * 安全检测结果值对象
 *
 * <p>调用方式：
 * <pre>
 *   safetyChecker.check(content).throwIfBlocked();
 * </pre>
 */
public record SafetyResult(boolean safe, String triggerType) {

    /** 内容安全 */
    public static SafetyResult pass() {
        return new SafetyResult(true, null);
    }

    /** 内容命中安全风险 */
    public static SafetyResult block(String triggerType) {
        return new SafetyResult(false, triggerType);
    }

    /**
     * 不安全时抛出 {@link SafetyBlockedException}（HTTP 451），安全时无操作。
     */
    public void throwIfBlocked() {
        if (!safe) {
            throw new SafetyBlockedException(triggerType);
        }
    }
}
