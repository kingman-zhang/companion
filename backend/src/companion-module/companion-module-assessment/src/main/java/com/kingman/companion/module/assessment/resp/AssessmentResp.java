package com.kingman.companion.module.assessment.resp;

import com.kingman.companion.component.enums.AssessmentLevel;
import com.kingman.companion.component.enums.RecommendedAction;
import com.kingman.companion.component.enums.UserPrimaryIntent;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评估结果响应体
 */
@Data
@Builder
public class AssessmentResp {

    private String assessmentId;

    /** 总分 0–100（规则计算） */
    private Integer score;

    /** red / yellow / green（规则计算） */
    private AssessmentLevel level;

    /** 置信度 0.0–1.0 */
    private Double confidence;

    // ---- 三维度得分（对应结果页进度条）----
    /** 情感联结得分 */
    private Integer emotionalConnectionScore;
    /** 沟通质量得分 */
    private Integer communicationScore;
    /** 冲突处理得分 */
    private Integer conflictScore;

    // ---- AI 生成内容 ----
    /** 核心洞察（引用体，一句话） */
    private String coreInsight;
    /** 洞察说明文字（≤80字） */
    private String llmReason;

    /** 推荐行动 */
    private RecommendedAction recommendedAction;

    /** 用户意图（影响结果页 CTA） */
    private UserPrimaryIntent userPrimaryIntent;

    private LocalDateTime createdAt;
}
