package com.kingman.companion.component.enums;

/**
 * 关系评估等级
 */
public enum AssessmentLevel {
    /** 绿色：score ≥ 65，情况相对乐观 */
    GREEN,
    /** 黄色：35 ≤ score < 65，需要谨慎处理 */
    YELLOW,
    /** 红色：score < 35 或触发 OVERRIDE，高风险 */
    RED
}
