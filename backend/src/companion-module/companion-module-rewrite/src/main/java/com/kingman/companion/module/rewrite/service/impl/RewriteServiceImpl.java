package com.kingman.companion.module.rewrite.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingman.companion.component.llm.LlmGateway;
import com.kingman.companion.component.llm.RoutingContext;
import com.kingman.companion.component.safety.SafetyChecker;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import com.kingman.companion.framework.security.AuthContext;
import com.kingman.companion.framework.util.DistributeID;
import com.kingman.companion.module.rewrite.config.RewriteProperties;
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

/**
 * 消息改写服务实现
 *
 * <p>调用 LLM 生成 gentle / direct / brief 三个改写变体，
 * 要求 LLM 严格返回 JSON，解析后持久化到 MongoDB。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RewriteServiceImpl implements RewriteService {

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
    private final RewriteProperties rewriteProperties;

    // ── Public API ────────────────────────────────────────────────────────────

    @Override
    public RewriteResp rewrite(RewriteReq req, String assessmentContext) {
        // 安全检测（前置，命中则 HTTP 451）
        safetyChecker.check(req.getOriginalMessage()).throwIfBlocked();

        // 免费层每日限额检查（登录用户时生效）
        if (hasUserId()) {
            checkDailyLimit(AuthContext.getCurrentUserId());
        }

        String systemPrompt = assessmentContext != null
                ? assessmentContext + "\n\n" + rewriteProperties.getSystemPrompt()
                : rewriteProperties.getSystemPrompt();
        String userPrompt = "请改写以下消息：\n\"%s\"".formatted(req.getOriginalMessage());
        String llmText = llmGateway.complete(systemPrompt, userPrompt, RoutingContext.standard());

        List<RewriteVariant> variants = parseVariants(llmText);

        RewriteRecord record = new RewriteRecord();
        record.setId(DistributeID.generate());
        record.setSessionId(req.getSessionId());
        record.setOriginalMessage(req.getOriginalMessage());
        record.setVariants(variants);
        RewriteRecord saved = rewriteRepository.save(record);

        // 限额内成功后记录使用
        if (hasUserId()) {
            recordUsage(AuthContext.getCurrentUserId());
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
            String json = extractJson(llmText);

            // Model returned plain text (no JSON) — use it as the gentle variant
            if (!json.startsWith("{")) {
                log.warn("改写 LLM 返回纯文本（非 JSON），降级处理: len={}", llmText.length());
                return plainTextFallback(llmText.trim());
            }

            JsonNode root = MAPPER.readTree(json);
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
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("改写结果解析失败，LLM 原始输出：{}", llmText, e);
            throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
        }
    }

    private List<RewriteVariant> plainTextFallback(String content) {
        return VERSIONS.stream().map(version -> {
            RewriteVariant v = new RewriteVariant();
            v.setVersion(version);
            v.setContent(content);
            v.setRiskLevel("low");
            v.setRiskReason("");
            v.setSendRecommended(true);
            v.setConfidence(0.6);
            return v;
        }).toList();
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

    private boolean hasUserId() {
        return AuthContext.getCurrentUserId() != null && !AuthContext.getCurrentUserId().isBlank();
    }

    /**
     * 检查今日是否已达上限，超限则抛出异常。
     */
    void checkDailyLimit(String userId) {
        LocalDate today = LocalDate.now();
        Optional<RewriteDailyUsage> usage = dailyUsageRepository.findByUserIdAndUsageDate(userId, today);
        if (usage.isPresent() && usage.get().getCount() >= DAILY_REWRITE_LIMIT) {
            throw new ApiException(CodeEnum.FREE_TIER_LIMIT_REACHED);
        }
    }

    /**
     * 记录本次使用（upsert：当日已有记录则 +1，否则新建）。
     */
    void recordUsage(String userId) {
        LocalDate today = LocalDate.now();
        Optional<RewriteDailyUsage> existing = dailyUsageRepository.findByUserIdAndUsageDate(userId, today);
        if (existing.isPresent()) {
            existing.get().setCount(existing.get().getCount() + 1);
            dailyUsageRepository.save(existing.get());
        } else {
            RewriteDailyUsage usage = new RewriteDailyUsage();
            usage.setId(DistributeID.generate());
            usage.setUserId(userId);
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
