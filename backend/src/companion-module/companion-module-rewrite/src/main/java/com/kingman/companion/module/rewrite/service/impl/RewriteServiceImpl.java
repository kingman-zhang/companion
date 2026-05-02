package com.kingman.companion.module.rewrite.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingman.companion.component.llm.LlmGateway;
import com.kingman.companion.component.llm.RoutingContext;
import com.kingman.companion.component.safety.SafetyChecker;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import com.kingman.companion.framework.util.DistributeID;
import com.kingman.companion.module.rewrite.entity.RewriteDailyUsage;
import com.kingman.companion.module.rewrite.entity.RewriteRecord;
import com.kingman.companion.module.rewrite.entity.RewriteVariant;
import com.kingman.companion.module.rewrite.repository.RewriteDailyUsageRepository;
import com.kingman.companion.module.rewrite.repository.RewriteRepository;
import com.kingman.companion.module.rewrite.req.RewriteReq;
import com.kingman.companion.module.rewrite.resp.RewriteResp;
import com.kingman.companion.module.rewrite.service.RewriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 消息改写服务实现
 *
 * <p>调用 Claude 生成 gentle / direct / brief 三个改写变体，
 * 要求 LLM 严格返回 JSON，解析后持久化到 MongoDB。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RewriteServiceImpl implements RewriteService {

    // ── Prompt ──────────────────────────────────────────────────────────────

    static final String SYSTEM_PROMPT = """
            你是一位专业情感沟通顾问。将用户发来的情绪化消息改写为三个不同风格的版本，帮助降低沟通冲突风险。

            改写版本说明：
            - gentle（温和版）：语气最柔和，减少对抗性，适合希望修复关系的场景
            - direct（直接版）：清晰理性地表达诉求，克制情绪，不带攻击性
            - brief（简短版）：控制在30字以内，轻描淡写，降低对方的防御感

            风险等级（risk_level）评定：
            - low：措辞温和，对方不易产生防御反应
            - medium：有一定压力感或明确诉求，总体可接受
            - high：措辞尖锐或有对抗性，可能激化冲突（此时 send_recommended 必须为 false）

            confidence：你对改写质量的自信程度，范围 0.0–1.0。
            risk_reason：不超过20字的风险说明。

            严格只输出以下 JSON，不要有任何前缀、解释或额外内容：
            {"gentle":{"content":"...","risk_level":"...","risk_reason":"...","send_recommended":true,"confidence":0.85},"direct":{"content":"...","risk_level":"...","risk_reason":"...","send_recommended":true,"confidence":0.80},"brief":{"content":"...","risk_level":"...","risk_reason":"...","send_recommended":true,"confidence":0.82}}
            """;

    /** 独立 ObjectMapper，不受全局 SNAKE_CASE 策略影响，直接按 path() 读取 JSON 节点 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> VERSIONS = List.of("gentle", "direct", "brief");

    /** 免费层每日改写上限 */
    static final int DAILY_REWRITE_LIMIT = 1;

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final RewriteRepository rewriteRepository;
    private final LlmGateway llmGateway;
    private final RewriteDailyUsageRepository dailyUsageRepository;
    private final SafetyChecker safetyChecker;

    // ── Public API ────────────────────────────────────────────────────────────

    @Override
    public RewriteResp rewrite(RewriteReq req) {
        // 安全检测（前置，命中则 HTTP 451）
        safetyChecker.check(req.getOriginalMessage()).throwIfBlocked();

        // 免费层每日限额检查（有 deviceId 时生效）
        if (hasDeviceId(req)) {
            checkDailyLimit(req.getDeviceId());
        }

        String userPrompt = "请改写以下消息：\n\"%s\"".formatted(req.getOriginalMessage());
        String llmText = llmGateway.complete(SYSTEM_PROMPT, userPrompt, RoutingContext.standard());

        List<RewriteVariant> variants = parseVariants(llmText);

        RewriteRecord record = new RewriteRecord();
        record.setId(DistributeID.generate());
        record.setSessionId(req.getSessionId());
        record.setOriginalMessage(req.getOriginalMessage());
        record.setVariants(variants);
        RewriteRecord saved = rewriteRepository.save(record);

        // 限额内成功后记录使用
        if (hasDeviceId(req)) {
            recordUsage(req.getDeviceId());
        }

        log.info("改写完成: id={}", saved.getId());
        return toResp(saved);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * 从 LLM 文本中提取 JSON 并解析为三个变体。
     *
     * <p>提取逻辑：找第一个 '{' 到最后一个 '}'，容忍 Claude 偶发的前缀文字。
     * risk_level=high 时强制 send_recommended=false，无论 LLM 返回什么值。
     */
    List<RewriteVariant> parseVariants(String llmText) {
        if (llmText == null || llmText.isBlank()) {
            throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
        }
        try {
            JsonNode root = MAPPER.readTree(extractJson(llmText));
            if (root == null || root.isMissingNode() || root.isNull()) {
                throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
            }
            return VERSIONS.stream()
                    .map(version -> {
                        JsonNode node = root.path(version);
                        RewriteVariant v = new RewriteVariant();
                        v.setVersion(version);
                        v.setContent(node.path("content").asText());
                        v.setRiskLevel(node.path("risk_level").asText("low"));
                        v.setRiskReason(node.path("risk_reason").asText(""));
                        v.setSendRecommended(node.path("send_recommended").asBoolean(true));
                        v.setConfidence(node.path("confidence").asDouble(0.8));
                        // 业务规则：高风险强制不建议发送
                        if ("high".equals(v.getRiskLevel())) {
                            v.setSendRecommended(false);
                        }
                        return v;
                    })
                    .toList();
        } catch (Exception e) {
            log.error("改写结果解析失败，LLM 原始输出：{}", llmText, e);
            throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
        }
    }

    /** 从可能含前缀文字的字符串中提取第一个完整 JSON 对象 */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return text;
        }
        return text.substring(start, end + 1);
    }

    // ── 免费层限额 ────────────────────────────────────────────────────────────

    private boolean hasDeviceId(RewriteReq req) {
        return req.getDeviceId() != null && !req.getDeviceId().isBlank();
    }

    /**
     * 检查今日是否已达上限，超限则抛出异常。
     */
    void checkDailyLimit(String deviceId) {
        LocalDate today = LocalDate.now();
        Optional<RewriteDailyUsage> usage = dailyUsageRepository.findByDeviceIdAndUsageDate(deviceId, today);
        if (usage.isPresent() && usage.get().getCount() >= DAILY_REWRITE_LIMIT) {
            throw new ApiException(CodeEnum.FREE_TIER_LIMIT_REACHED);
        }
    }

    /**
     * 记录本次使用（upsert：当日已有记录则 +1，否则新建）。
     */
    void recordUsage(String deviceId) {
        LocalDate today = LocalDate.now();
        Optional<RewriteDailyUsage> existing = dailyUsageRepository.findByDeviceIdAndUsageDate(deviceId, today);
        if (existing.isPresent()) {
            existing.get().setCount(existing.get().getCount() + 1);
            dailyUsageRepository.save(existing.get());
        } else {
            RewriteDailyUsage usage = new RewriteDailyUsage();
            usage.setId(DistributeID.generate());
            usage.setDeviceId(deviceId);
            usage.setUsageDate(today);
            usage.setCount(1);
            dailyUsageRepository.save(usage);
        }
    }

    private RewriteResp toResp(RewriteRecord record) {
        return RewriteResp.builder()
                .rewriteId(record.getId())
                .variants(record.getVariants().stream()
                        .map(v -> RewriteResp.VariantResp.builder()
                                .version(v.getVersion())
                                .content(v.getContent())
                                .riskLevel(v.getRiskLevel())
                                .riskReason(v.getRiskReason())
                                .sendRecommended(v.getSendRecommended())
                                .confidence(v.getConfidence())
                                .build())
                        .toList())
                .createdAt(record.getCreateTime())
                .build();
    }
}
