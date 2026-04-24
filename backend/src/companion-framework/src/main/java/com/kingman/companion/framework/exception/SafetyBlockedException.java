package com.kingman.companion.framework.exception;

import lombok.Getter;

/**
 * 安全拦截异常（HTTP 451）
 */
@Getter
public class SafetyBlockedException extends RuntimeException {

    /** 触发类型：self_harm / violence / abuse_flags */
    private final String triggerType;

    public SafetyBlockedException(String triggerType) {
        super("检测到安全风险，已中断当前操作");
        this.triggerType = triggerType;
    }
}
