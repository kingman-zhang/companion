package com.kingman.companion.api.service;

import com.kingman.companion.component.enums.*;
import com.kingman.companion.component.llm.LlmGateway;
import com.kingman.companion.component.llm.RoutingContext;
import com.kingman.companion.module.assessment.config.AssessmentProperties;
import com.kingman.companion.module.assessment.resp.AssessmentResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 将评估结果转换为聊天 system prompt 用的用户背景摘要。
 *
 * <p>先把问卷原始答案和评分结果拼成结构化输入，调 LLM 生成自然语言摘要；
 * LLM 失败时降级为规则拼接文本，保证主流程不阻断。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentContextBuilder {

    private final LlmGateway llmGateway;
    private final AssessmentProperties assessmentProperties;

    /**
     * 生成用户背景摘要字符串，用于注入聊天 system prompt 头部。
     *
     * <p>返回格式：
     * <pre>
     * 【用户背景（来自关系评估）】
     * ...LLM 生成的自然语言摘要...
     * ──────────────────────────────────
     * 请根据以上背景陪伴用户，不要主动提及"评估"二字。
     * </pre>
     */
    public String build(AssessmentResp a) {
        String summary = generateSummary(a);
        return """
                【用户背景（来自关系评估）】
                %s
                ──────────────────────────────────
                请根据以上背景陪伴用户，不要主动提及"评估"二字。
                """.formatted(summary);
    }

    // ── LLM 摘要生成 ──────────────────────────────────────────────────────────

    private String generateSummary(AssessmentResp a) {
        try {
            String userMessage = buildInputText(a);
            String summary = llmGateway.complete(assessmentProperties.getContextSummaryPrompt(), userMessage, RoutingContext.chat(
                    userMessage.length(), com.kingman.companion.component.safety.SafetyLevel.SAFE));
            log.info("评估背景摘要生成成功 assessmentId={}:\n{}", a.getAssessmentId(), summary);
            return summary;
        } catch (Exception e) {
            log.warn("评估背景摘要 LLM 调用失败，降级为规则文本: {}", e.getMessage());
            return buildFallbackSummary(a);
        }
    }

    private String buildInputText(AssessmentResp a) {
        return """
                关系风险等级：%s（综合分 %d / 100）
                情感联结：%d分　沟通质量：%d分　冲突处理：%d分

                问卷答案：
                Q1 交往时长：%s
                Q2 分手方式：%s
                Q3 当前情绪：%s
                Q4 分手前沟通：%s
                Q5 冲突模式：%s
                Q6 对方情感判断：%s
                Q7 用户目标：%s

                AI 洞察（供参考）：%s
                评估说明：%s
                """.formatted(
                levelLabel(a.getLevel()), nullInt(a.getScore()),
                nullInt(a.getEmotionalConnectionScore()),
                nullInt(a.getCommunicationScore()),
                nullInt(a.getConflictScore()),
                durationLabel(a.getRelationshipDuration()),
                breakupLabel(a.getBreakupMethod()),
                emotionLabel(a.getCurrentEmotion()),
                communicationLabel(a.getCommunicationQuality()),
                conflictLabel(a.getConflictStyle()),
                partnerLabel(a.getPartnerLovePerception()),
                intentLabel(a.getUserPrimaryIntent()),
                nullStr(a.getCoreInsight()),
                nullStr(a.getLlmReason())
        );
    }

    /** LLM 失败时的规则降级文本 */
    private String buildFallbackSummary(AssessmentResp a) {
        return "该用户正处于关系危机中，关系风险等级为%s（综合分 %d）。交往%s后因%s提出分手，目前情绪状态为%s。用户当前目标是%s，推荐方向：%s。"
                .formatted(
                        levelLabel(a.getLevel()), nullInt(a.getScore()),
                        durationLabel(a.getRelationshipDuration()),
                        breakupLabel(a.getBreakupMethod()),
                        emotionLabel(a.getCurrentEmotion()),
                        intentLabel(a.getUserPrimaryIntent()),
                        actionLabel(a.getRecommendedAction())
                );
    }

    // ── 枚举中文标签 ──────────────────────────────────────────────────────────

    private String levelLabel(AssessmentLevel v) {
        if (v == null) return "未知";
        return switch (v) {
            case RED -> "红色（高风险）";
            case YELLOW -> "黄色（需谨慎）";
            case GREEN -> "绿色（相对乐观）";
        };
    }

    private String durationLabel(RelationshipDuration v) {
        if (v == null) return "未知";
        return switch (v) {
            case LESS_THAN_3M -> "不到3个月";
            case SIX_MONTHS_TO_2Y -> "半年到2年";
            case TWO_TO_5Y -> "2到5年";
            case MORE_THAN_5Y -> "5年以上";
        };
    }

    private String breakupLabel(BreakupMethod v) {
        if (v == null) return "未知";
        return switch (v) {
            case FACE_TO_FACE_CALM -> "当面冷静提出";
            case DURING_ARGUMENT -> "吵架中爆发提出";
            case MESSAGE -> "通过微信/消息提出";
            case GHOSTED -> "直接消失/拉黑";
        };
    }

    private String emotionLabel(CurrentEmotion v) {
        if (v == null) return "未知";
        return switch (v) {
            case SHOCKED -> "震惊，脑子空白";
            case ANGRY -> "愤怒，觉得被背叛";
            case SAD -> "悲伤，反复想起过去";
            case DETERMINED -> "不甘，想争回来";
        };
    }

    private String communicationLabel(CommunicationQuality v) {
        if (v == null) return "未知";
        return switch (v) {
            case GOOD_DAILY -> "沟通顺畅，偶有摩擦";
            case SURFACE_LEVEL -> "表面正常，各聊各的";
            case FREQUENT_CONFLICT -> "冷战居多，开口就吵";
            case PARTNER_COLD -> "对方一直比较冷淡";
        };
    }

    private String conflictLabel(ConflictStyle v) {
        if (v == null) return "未知";
        return switch (v) {
            case RESOLVE_AFTER_CALM -> "冷静后会聊清楚";
            case AVOID_THEN_IGNORE -> "各自冷几天，不了了之";
            case ONE_SIDED_APOLOGY -> "一方道歉，另一方不回应";
            case ESCALATE_DIG_UP_PAST -> "翻旧账，互相伤害";
        };
    }

    private String partnerLabel(PartnerLovePerception v) {
        if (v == null) return "未知";
        return switch (v) {
            case YES_EXTERNAL_PRESSURE -> "爱，但被现实压垮";
            case UNSURE_CHANGED -> "不确定，对方变了很多";
            case MAYBE_NOT_CANT_LET_GO -> "可能不爱了，但放不下";
            case NO_JUST_CANT_MOVE_ON -> "不爱了，只是还没放下";
        };
    }

    private String intentLabel(UserPrimaryIntent v) {
        if (v == null) return "未明确";
        return switch (v) {
            case RECONCILE -> "想挽回对方";
            case PROCESS_EMOTION_FIRST -> "先处理情绪，再决定";
            case LEARN_GOODBYE -> "学会好好告别";
            case CHAT_FIRST -> "说不清，先聊聊";
        };
    }

    private String actionLabel(RecommendedAction v) {
        if (v == null) return "未明确";
        return switch (v) {
            case SEEK_PROFESSIONAL_HELP -> "建议寻求专业帮助";
            case COOL_DOWN -> "建议先冷静，暂时不联系";
            case TRY_COMMUNICATION -> "可以尝试沟通";
            case CONSIDER_RECONCILE -> "可以考虑挽回计划";
            case LET_GO -> "放手可能是更好的选择";
        };
    }

    private int nullInt(Integer v) { return v != null ? v : 0; }
    private String nullStr(String v) { return v != null ? v : "（暂无）"; }
}
