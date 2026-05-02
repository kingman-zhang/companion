package com.kingman.companion.component.safety;

/**
 * 安全检测分级
 *
 * <ul>
 *   <li>{@link #SAFE} — 内容安全，正常路由</li>
 *   <li>{@link #CONCERNING} — 内容令人担忧（被动/模糊表达），路由到 SAFETY 模型 + 安全回复策略，不阻断</li>
 *   <li>{@link #BLOCKED} — 明确危险内容，直接抛 {@link com.kingman.companion.framework.exception.SafetyBlockedException}（HTTP 451）</li>
 * </ul>
 */
public enum SafetyLevel {
    SAFE,
    CONCERNING,
    BLOCKED
}
