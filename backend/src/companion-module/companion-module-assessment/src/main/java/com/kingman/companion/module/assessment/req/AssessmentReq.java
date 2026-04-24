package com.kingman.companion.module.assessment.req;

import com.kingman.companion.component.enums.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交关系评估问卷请求体（7题）
 */
@Data
public class AssessmentReq {

    /** 会话 ID（可选，用于关联上下文） */
    private String sessionId;

    /** P0 情绪状态（埋点用，不参与评分） */
    private EntryState entryState;

    /** Q1: 你们在一起多久了 */
    @NotNull(message = "Q1 交往时长不能为空")
    private RelationshipDuration relationshipDuration;

    /** Q2: TA是怎么提出来的 */
    @NotNull(message = "Q2 分手方式不能为空")
    private BreakupMethod breakupMethod;

    /** Q3: 现在你最强烈的感受是 */
    @NotNull(message = "Q3 当前情绪不能为空")
    private CurrentEmotion currentEmotion;

    /** Q4: 分手前3个月,你们的沟通怎样 */
    @NotNull(message = "Q4 沟通质量不能为空")
    private CommunicationQuality communicationQuality;

    /** Q5: 你们吵架时,通常会 */
    @NotNull(message = "Q5 冲突处理方式不能为空")
    private ConflictStyle conflictStyle;

    /** Q6: 你觉得TA还爱你吗 */
    @NotNull(message = "Q6 对方情感判断不能为空")
    private PartnerLovePerception partnerLovePerception;

    /** Q7: 现在你最想要什么（不参与评分，影响结果页流向） */
    @NotNull(message = "Q7 用户意图不能为空")
    private UserPrimaryIntent userPrimaryIntent;
}
