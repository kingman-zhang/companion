package com.kingman.companion.component.llm;

/**
 * 模型调用层级
 *
 * <p>决定路由到哪一档模型，各层级对应 {@code companion.llm.router.chains} 配置的 fallback 链。
 */
public enum ModelTier {
    /** 普通聊天：便宜、快速 */
    LITE,
    /** 标准任务：改写、评估解读 */
    STANDARD,
    /** 深度分析：高端模型，需要更强推理 */
    ADVANCED,
    /** 长上下文：聊天记录上传等大输入场景 */
    LONG_CONTEXT,
    /** 风险用户：携带安全回复策略的专属 system prompt */
    SAFETY
}
