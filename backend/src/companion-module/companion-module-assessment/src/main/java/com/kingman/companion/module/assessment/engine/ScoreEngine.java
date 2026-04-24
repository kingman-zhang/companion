package com.kingman.companion.module.assessment.engine;

import com.kingman.companion.component.enums.*;
import com.kingman.companion.module.assessment.req.AssessmentReq;
import org.springframework.stereotype.Component;

/**
 * 关系评估规则引擎 — 三维度评分模型
 *
 * 维度权重：
 *   情感联结 (EC)  40%
 *   沟通质量 (CS)  35%
 *   冲突处理 (CF)  25%
 *
 * 分级：GREEN ≥ 65 / YELLOW 35-64 / RED < 35
 */
@Component
public class ScoreEngine {

    // 维度权重
    private static final double W_EMOTIONAL = 0.40;
    private static final double W_COMMUNICATION = 0.35;
    private static final double W_CONFLICT = 0.25;

    public ScoreResult calculate(AssessmentReq req) {

        int ec = calcEmotionalConnection(req);
        int cs = calcCommunication(req);
        int cf = calcConflict(req);

        int total = (int) Math.round(ec * W_EMOTIONAL + cs * W_COMMUNICATION + cf * W_CONFLICT);

        AssessmentLevel level = determineLevel(total);
        double confidence = calcConfidence(req);
        RecommendedAction action = determineAction(level, req.getUserPrimaryIntent());
        String coreInsight = generateInsight(ec, cs, cf, req);
        String reason = generateReason(level, ec, cs, cf);

        return new ScoreResult(total, level, confidence, ec, cs, cf, action, coreInsight, reason);
    }

    // ---- 情感联结：Q1(30%) + Q2(20%) + Q6(50%) ----

    private int calcEmotionalConnection(AssessmentReq req) {
        double duration = scoreDuration(req.getRelationshipDuration());
        double breakup = scoreBreakupMethod(req.getBreakupMethod());
        double partnerLove = scorePartnerLove(req.getPartnerLovePerception());
        return (int) Math.round(duration * 0.30 + breakup * 0.20 + partnerLove * 0.50);
    }

    private double scoreDuration(RelationshipDuration d) {
        if (d == null) return 50;
        return switch (d) {
            case LESS_THAN_3M -> 45;
            case SIX_MONTHS_TO_2Y -> 65;
            case TWO_TO_5Y -> 80;
            case MORE_THAN_5Y -> 88;
        };
    }

    private double scoreBreakupMethod(BreakupMethod m) {
        if (m == null) return 50;
        return switch (m) {
            case DURING_ARGUMENT -> 72;   // 情绪决定，未必最终
            case FACE_TO_FACE_CALM -> 52; // 深思熟虑，较难挽回
            case MESSAGE -> 48;           // 逃避面对，有压力但回避
            case GHOSTED -> 22;           // 回避型，信号较差
        };
    }

    private double scorePartnerLove(PartnerLovePerception p) {
        if (p == null) return 50;
        return switch (p) {
            case YES_EXTERNAL_PRESSURE -> 88;
            case UNSURE_CHANGED -> 60;
            case MAYBE_NOT_CANT_LET_GO -> 38;
            case NO_JUST_CANT_MOVE_ON -> 20;
        };
    }

    // ---- 沟通质量：Q4(100%) ----

    private int calcCommunication(AssessmentReq req) {
        if (req.getCommunicationQuality() == null) return 50;
        return switch (req.getCommunicationQuality()) {
            case GOOD_DAILY -> 90;
            case SURFACE_LEVEL -> 62;
            case FREQUENT_CONFLICT -> 30;
            case PARTNER_COLD -> 25;
        };
    }

    // ---- 冲突处理：Q5(100%) ----

    private int calcConflict(AssessmentReq req) {
        if (req.getConflictStyle() == null) return 50;
        return switch (req.getConflictStyle()) {
            case RESOLVE_AFTER_CALM -> 90;
            case AVOID_THEN_IGNORE -> 58;
            case ONE_SIDED_APOLOGY -> 35;
            case ESCALATE_DIG_UP_PAST -> 20;
        };
    }

    // ---- 分级 ----

    private AssessmentLevel determineLevel(int score) {
        if (score >= 65) return AssessmentLevel.GREEN;
        if (score >= 35) return AssessmentLevel.YELLOW;
        return AssessmentLevel.RED;
    }

    // ---- 置信度（所有 7 题是否填写完整）----

    private double calcConfidence(AssessmentReq req) {
        int filled = 0;
        if (req.getRelationshipDuration() != null) filled++;
        if (req.getBreakupMethod() != null) filled++;
        if (req.getCurrentEmotion() != null) filled++;
        if (req.getCommunicationQuality() != null) filled++;
        if (req.getConflictStyle() != null) filled++;
        if (req.getPartnerLovePerception() != null) filled++;
        if (req.getUserPrimaryIntent() != null) filled++;
        return Math.round((double) filled / 7 * 10.0) / 10.0;
    }

    // ---- 推荐行动 ----

    private RecommendedAction determineAction(AssessmentLevel level, UserPrimaryIntent intent) {
        if (level == AssessmentLevel.RED) return RecommendedAction.SEEK_PROFESSIONAL_HELP;
        if (intent == UserPrimaryIntent.LEARN_GOODBYE) return RecommendedAction.LET_GO;
        if (intent == UserPrimaryIntent.PROCESS_EMOTION_FIRST || intent == UserPrimaryIntent.CHAT_FIRST) {
            return RecommendedAction.COOL_DOWN;
        }
        if (level == AssessmentLevel.GREEN) return RecommendedAction.CONSIDER_RECONCILE;
        return RecommendedAction.COOL_DOWN;
    }

    // ---- 核心洞察生成（规则模板，后续替换为 LLM）----

    private String generateInsight(int ec, int cs, int cf, AssessmentReq req) {
        // 找出最弱维度
        if (cs <= cf && cs <= ec) {
            return "你们缺的不是感情，是有效的沟通方式。";
        }
        if (cf <= cs && cf <= ec) {
            if (req.getConflictStyle() == ConflictStyle.AVOID_THEN_IGNORE) {
                return "你们缺的不是感情，是喘息空间。";
            }
            return "冲突模式在消耗这段关系的能量。";
        }
        if (req.getPartnerLovePerception() == PartnerLovePerception.YES_EXTERNAL_PRESSURE) {
            return "外部压力是主因，感情基础还在。";
        }
        if (req.getBreakupMethod() == BreakupMethod.DURING_ARGUMENT) {
            return "这可能是一次情绪爆发，未必是最终决定。";
        }
        return "关系有修复空间，但需要改变互动模式。";
    }

    private String generateReason(AssessmentLevel level, int ec, int cs, int cf) {
        return switch (level) {
            case GREEN -> "情感基础较好，沟通和冲突处理有一定问题但可以改善。建议先给彼此空间，再寻找合适时机沟通。";
            case YELLOW -> {
                if (cs < 50) yield "频繁追问让 TA 退缩 — 先停手 3 天，观察 TA 主动联系的意愿。";
                if (cf < 50) yield "冲突处理方式需要改变，否则即使挽回也容易再次分开。";
                yield "关系处于敏感期，有些因素对挽回有利，但也存在障碍。先稳定自己的情绪。";
            }
            case RED -> "当前情况较复杂，直接介入效果有限。先照顾好自己，必要时寻求专业支持。";
        };
    }

    /**
     * 评分结果值对象
     */
    public record ScoreResult(
            int score,
            AssessmentLevel level,
            double confidence,
            int emotionalConnectionScore,
            int communicationScore,
            int conflictScore,
            RecommendedAction recommendedAction,
            String coreInsight,
            String reason
    ) {}
}
