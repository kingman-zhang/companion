package com.kingman.companion.module.rewrite.entity;

import lombok.Data;

/**
 * 改写变体（嵌入 RewriteRecord）
 */
@Data
public class RewriteVariant {

    /** gentle / direct / brief */
    private String version;
    /** 改写内容，≤500 字 */
    private String content;
    /** low / medium / high */
    private String riskLevel;
    /** 风险说明，≤30 字 */
    private String riskReason;
    /** 是否建议发送（risk_level=high 时强制 false） */
    private Boolean sendRecommended;
    /** 置信度 0.0–1.0 */
    private Double confidence;
}
