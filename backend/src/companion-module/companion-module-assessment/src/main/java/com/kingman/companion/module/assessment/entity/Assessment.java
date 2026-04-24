package com.kingman.companion.module.assessment.entity;

import com.kingman.companion.component.enums.*;
import com.kingman.companion.framework.common.AbstractBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 关系评估记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "assessments")
public class Assessment extends AbstractBaseEntity {

    private String sessionId;

    // ---- 问卷输入（7题）----
    /** Q0: P0 情绪状态（埋点用） */
    private EntryState entryState;
    /** Q1: 交往时长 */
    private RelationshipDuration relationshipDuration;
    /** Q2: TA是怎么提出来的 */
    private BreakupMethod breakupMethod;
    /** Q3: 现在最强烈的感受 */
    private CurrentEmotion currentEmotion;
    /** Q4: 分手前沟通质量 */
    private CommunicationQuality communicationQuality;
    /** Q5: 吵架处理方式 */
    private ConflictStyle conflictStyle;
    /** Q6: 你觉得TA还爱你吗 */
    private PartnerLovePerception partnerLovePerception;
    /** Q7: 现在最想要什么（不参与评分） */
    private UserPrimaryIntent userPrimaryIntent;

    // ---- 规则计算结果 ----
    /** 总分 0–100 */
    private Integer score;
    private AssessmentLevel level;
    /** 置信度 0.0–1.0 */
    private Double confidence;

    /** 三维度得分 */
    private Integer emotionalConnectionScore;  // 情感联结
    private Integer communicationScore;        // 沟通质量
    private Integer conflictScore;             // 冲突处理

    // ---- LLM 生成内容 ----
    /** 核心洞察一句话（引用体） */
    private String coreInsight;
    /** 洞察说明（≤80字） */
    private String llmReason;
    private RecommendedAction recommendedAction;
}
