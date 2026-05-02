package com.kingman.companion.module.assessment.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingman.companion.component.llm.LlmGateway;
import com.kingman.companion.component.llm.RoutingContext;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import com.kingman.companion.framework.util.DistributeID;
import com.kingman.companion.module.assessment.engine.ScoreEngine;
import com.kingman.companion.module.assessment.entity.Assessment;
import com.kingman.companion.module.assessment.repository.AssessmentRepository;
import com.kingman.companion.module.assessment.req.AssessmentReq;
import com.kingman.companion.module.assessment.resp.AssessmentResp;
import com.kingman.companion.module.assessment.resp.QuestionDef;
import com.kingman.companion.module.assessment.resp.QuestionOption;
import com.kingman.companion.module.assessment.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentServiceImpl implements AssessmentService {

    // ── Prompt ──────────────────────────────────────────────────────────────

    static final String SYSTEM_PROMPT = """
            你是"伴听"的关系评估分析师。根据用户问卷答案和三维度评分结果，生成两段评估文字：
            - core_insight：≤15字的核心洞察，直接、精准、有共情感，帮用户一句话看清问题
            - llm_reason：100-300字的评估说明，从情感联结、沟通质量、冲突处理三个维度分析现状，语气温暖但不回避问题，结尾给出一条具体的下一步建议

            严格只输出以下 JSON，不要有任何前缀、解释或额外内容：
            {"core_insight":"...","llm_reason":"..."}
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final AssessmentRepository assessmentRepository;
    private final ScoreEngine scoreEngine;
    private final LlmGateway llmGateway;

    @Override
    public List<QuestionDef> getQuestionnaire() {
        return List.of(
                new QuestionDef("Q1", "relationship_duration", "关系背景",
                        "你们在一起多久了？", "关系时长决定了依赖程度，也影响着分手的复杂度。", "SINGLE", List.of(
                        new QuestionOption("LESS_THAN_3M",       "不到3个月",         "磨合期 · 信任尚在建立"),
                        new QuestionOption("SIX_MONTHS_TO_2Y",  "半年到2年",          "关系已稳定 · 模式成型"),
                        new QuestionOption("TWO_TO_5Y",          "2到5年",             "深度绑定 · 有共同规划"),
                        new QuestionOption("MORE_THAN_5Y",       "5年以上",            "长期关系 · 牵涉更多")
                )),
                new QuestionDef("Q2", "breakup_method", "现在的状况",
                        "TA 是怎么提出来的？", "对方提分手的方式，直接反映了这段关系当下的情绪温度。", "SINGLE", List.of(
                        new QuestionOption("FACE_TO_FACE_CALM",  "当面，冷静地说",     "可能已经想了很久"),
                        new QuestionOption("DURING_ARGUMENT",    "吵架中爆发",         "情绪下的决定，未必最终"),
                        new QuestionOption("MESSAGE",            "微信 / 消息",        "逃避面对面，说明有压力"),
                        new QuestionOption("GHOSTED",            "直接消失 / 拉黑",    "回避型结束，需另行判断")
                )),
                new QuestionDef("Q3", "current_emotion", "情绪感受",
                        "现在你最强烈的感受是？", "此刻的感受，是你接下来所有行动的起点。", "SINGLE", List.of(
                        new QuestionOption("SHOCKED",            "震惊，感觉像在做梦", "还没接受这个现实"),
                        new QuestionOption("ANGRY",              "愤怒，觉得不公平",   "委屈和怒火交织"),
                        new QuestionOption("SAD",                "难过，很想念TA",     "思念让人窒息"),
                        new QuestionOption("DETERMINED",         "想搞清楚，冷静理性", "理智在线，需要方向")
                )),
                new QuestionDef("Q4", "communication_quality", "沟通质量",
                        "分手前3个月，你们的沟通怎样？", "沟通模式往往早于分手出现裂缝。", "SINGLE", List.of(
                        new QuestionOption("GOOD_DAILY",         "日常沟通顺畅，偶有摩擦", "基础关系健康"),
                        new QuestionOption("SURFACE_LEVEL",      "表面平静，但很少深聊",   "有距离但没爆发"),
                        new QuestionOption("FREQUENT_CONFLICT",  "频繁争吵或冷战",         "关系已经在消耗"),
                        new QuestionOption("PARTNER_COLD",       "TA 开始冷漠、回避我",    "单方面疏远信号")
                )),
                new QuestionDef("Q5", "conflict_style", "冲突模式",
                        "你们吵架时，通常会？", "处理冲突的方式，决定了关系能否真正修复。", "SINGLE", List.of(
                        new QuestionOption("RESOLVE_AFTER_CALM",     "冷静一下，再沟通解决",   "成熟型冲突处理"),
                        new QuestionOption("AVOID_THEN_IGNORE",      "先冷战，慢慢不了了之",   "问题被压下去，未解决"),
                        new QuestionOption("ONE_SIDED_APOLOGY",      "一方主动道歉，另一方接受", "有人承担，但不平衡"),
                        new QuestionOption("ESCALATE_DIG_UP_PAST",   "越吵越激烈，翻旧账",     "模式破坏性较强")
                )),
                new QuestionDef("Q6", "partner_love_perception", "感情判断",
                        "你觉得 TA 还爱你吗？", "你的直觉，往往比你以为的更准确。", "SINGLE", List.of(
                        new QuestionOption("YES_EXTERNAL_PRESSURE",  "爱，但被外部因素影响",   "压力 / 家人 / 现实阻隔"),
                        new QuestionOption("UNSURE_CHANGED",         "说不准，TA 好像变了",    "感情信号混乱"),
                        new QuestionOption("MAYBE_NOT_CANT_LET_GO",  "可能不爱了，但我放不下", "单方面深情"),
                        new QuestionOption("NO_JUST_CANT_MOVE_ON",   "不爱了，只是我没接受",   "需要正视现实")
                )),
                new QuestionDef("Q7", "user_primary_intent", "你的方向",
                        "现在你最想要什么？", "你现在最需要的，是解决方案还是被理解？", "SINGLE", List.of(
                        new QuestionOption("RECONCILE",              "想挽回，重新在一起",     "需要策略和时机"),
                        new QuestionOption("PROCESS_EMOTION_FIRST",  "先处理好自己的情绪",     "稳住是第一步"),
                        new QuestionOption("LEARN_GOODBYE",          "想学会放下",             "选择向前走"),
                        new QuestionOption("CHAT_FIRST",             "还没想好，先聊聊",       "边聊边想清楚")
                ))
        );
    }

    @Override
    public AssessmentResp submit(AssessmentReq req) {
        ScoreEngine.ScoreResult result = scoreEngine.calculate(req);

        // LLM 生成 core_insight + llm_reason；失败时降级到规则模板
        LlmEnrichment enrichment = llmEnrich(result, req);

        Assessment a = new Assessment();
        a.setId(DistributeID.generate());
        a.setSessionId(req.getSessionId());
        a.setEntryState(req.getEntryState());
        a.setRelationshipDuration(req.getRelationshipDuration());
        a.setBreakupMethod(req.getBreakupMethod());
        a.setCurrentEmotion(req.getCurrentEmotion());
        a.setCommunicationQuality(req.getCommunicationQuality());
        a.setConflictStyle(req.getConflictStyle());
        a.setPartnerLovePerception(req.getPartnerLovePerception());
        a.setUserPrimaryIntent(req.getUserPrimaryIntent());
        a.setScore(result.score());
        a.setLevel(result.level());
        a.setConfidence(result.confidence());
        a.setEmotionalConnectionScore(result.emotionalConnectionScore());
        a.setCommunicationScore(result.communicationScore());
        a.setConflictScore(result.conflictScore());
        a.setRecommendedAction(result.recommendedAction());
        a.setCoreInsight(enrichment.coreInsight());
        a.setLlmReason(enrichment.llmReason());

        Assessment saved = assessmentRepository.save(a);
        log.info("评估完成: id={}, score={}, level={}, EC={}, CS={}, CF={}",
                saved.getId(), result.score(), result.level(),
                result.emotionalConnectionScore(), result.communicationScore(), result.conflictScore());

        return toResp(saved);
    }

    @Override
    public AssessmentResp findById(String assessmentId) {
        Assessment a = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ApiException(CodeEnum.NOT_FOUND));
        return toResp(a);
    }

    // ── LLM 增强 ──────────────────────────────────────────────────────────────

    /**
     * 调用 LLM 生成评估洞察文字。
     *
     * <p>任何异常静默降级，使用规则引擎的模板文字兜底，保证主流程不中断。
     */
    LlmEnrichment llmEnrich(ScoreEngine.ScoreResult result, AssessmentReq req) {
        try {
            String userMessage = buildEnrichPrompt(result, req);
            String llmText = llmGateway.complete(SYSTEM_PROMPT, userMessage, RoutingContext.standard());
            return parseLlmEnrichment(llmText);
        } catch (Exception e) {
            log.warn("评估 LLM 增强失败，降级使用规则模板: {}", e.getMessage());
            return new LlmEnrichment(result.coreInsight(), result.reason());
        }
    }

    private String buildEnrichPrompt(ScoreEngine.ScoreResult result, AssessmentReq req) {
        return """
                评估等级：%s（综合分 %d）
                情感联结得分：%d / 100
                沟通质量得分：%d / 100
                冲突处理得分：%d / 100

                关键答案：
                - 关系时长：%s
                - 分手方式：%s
                - 对方情感判断：%s
                - 分手前沟通质量：%s
                - 冲突处理风格：%s
                - 用户当前目标：%s

                请根据以上信息生成 core_insight 和 llm_reason。
                """.formatted(
                result.level(), result.score(),
                result.emotionalConnectionScore(),
                result.communicationScore(),
                result.conflictScore(),
                nullSafe(req.getRelationshipDuration()),
                nullSafe(req.getBreakupMethod()),
                nullSafe(req.getPartnerLovePerception()),
                nullSafe(req.getCommunicationQuality()),
                nullSafe(req.getConflictStyle()),
                nullSafe(req.getUserPrimaryIntent())
        );
    }

    /**
     * 从 LLM 文本中提取并解析 JSON：{@code {"core_insight":"...","llm_reason":"..."}}
     */
    LlmEnrichment parseLlmEnrichment(String llmText) {
        if (llmText == null || llmText.isBlank()) {
            throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
        }
        try {
            JsonNode root = MAPPER.readTree(extractJson(llmText));
            if (root == null || root.isMissingNode() || root.isNull()) {
                throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
            }
            String coreInsight = root.path("core_insight").asText();
            String llmReason = root.path("llm_reason").asText();
            if (coreInsight.isBlank() || llmReason.isBlank()) {
                throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
            }
            return new LlmEnrichment(coreInsight, llmReason);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("评估 LLM 响应解析失败：{}", llmText, e);
            throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return text;
        return text.substring(start, end + 1);
    }

    private String nullSafe(Object o) {
        return o == null ? "未填写" : o.toString();
    }

    /** LLM 增强结果 */
    record LlmEnrichment(String coreInsight, String llmReason) {}

    private AssessmentResp toResp(Assessment a) {
        return AssessmentResp.builder()
                .assessmentId(a.getId())
                .score(a.getScore())
                .level(a.getLevel())
                .confidence(a.getConfidence())
                .emotionalConnectionScore(a.getEmotionalConnectionScore())
                .communicationScore(a.getCommunicationScore())
                .conflictScore(a.getConflictScore())
                .coreInsight(a.getCoreInsight())
                .llmReason(a.getLlmReason())
                .recommendedAction(a.getRecommendedAction())
                .userPrimaryIntent(a.getUserPrimaryIntent())
                .createdAt(a.getCreateTime())
                .build();
    }
}
