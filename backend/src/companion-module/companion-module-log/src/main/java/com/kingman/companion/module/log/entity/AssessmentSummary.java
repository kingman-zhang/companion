package com.kingman.companion.module.log.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * assessments 集合的只读投影——log 模块内使用，避免跨模块依赖。
 * 只映射 AI 建议生成所需的字段。
 */
@Data
@Document(collection = "assessments")
public class AssessmentSummary {

    @Id
    private String id;

    private String userId;

    /** GREEN / YELLOW / RED */
    private String level;

    /** 核心洞察一句话 */
    private String coreInsight;

    /** 洞察说明（≤80字） */
    private String llmReason;
}
