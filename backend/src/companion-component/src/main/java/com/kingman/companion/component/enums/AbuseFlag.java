package com.kingman.companion.component.enums;

/**
 * Q5: 安全风险事件（权重 0.25）
 * 非 NONE 触发 HTTP 451 安全拦截
 */
public enum AbuseFlag {
    /** 无安全风险 */
    NONE,
    /** 家暴或身体伤害 → OVERRIDE_RED */
    DOMESTIC_VIOLENCE,
    /** 明确暴力威胁 → OVERRIDE_RED */
    VIOLENCE_THREAT,
    /** 自伤/自杀风险 → OVERRIDE_RED */
    SELF_HARM,
    /** 严重控制或跟踪 → OVERRIDE_RED */
    STALKING_CONTROL
}
