package com.kingman.companion.module.rewrite.service.impl;

import com.kingman.companion.component.llm.LlmGateway;
import com.kingman.companion.component.llm.RoutingContext;
import com.kingman.companion.component.safety.SafetyChecker;
import com.kingman.companion.component.safety.SafetyResult;
import com.kingman.companion.framework.exception.ApiException;
import com.kingman.companion.framework.exception.SafetyBlockedException;
import com.kingman.companion.module.rewrite.config.RewriteProperties;
import com.kingman.companion.module.rewrite.entity.RewriteDailyUsage;
import com.kingman.companion.module.rewrite.entity.RewriteRecord;
import com.kingman.companion.module.rewrite.entity.RewriteVariant;
import com.kingman.companion.module.rewrite.repository.RewriteDailyUsageRepository;
import com.kingman.companion.module.rewrite.repository.RewriteRepository;
import com.kingman.companion.module.rewrite.req.RewriteReq;
import com.kingman.companion.module.rewrite.resp.RewriteResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P1 + P2 验收测试：RewriteServiceImpl LLM 接入 & 免费层每日限额
 */
@ExtendWith(MockitoExtension.class)
class RewriteServiceImplTest {

    @Mock
    private RewriteRepository rewriteRepository;

    @Mock
    private LlmGateway llmGateway;

    @Mock
    private RewriteDailyUsageRepository dailyUsageRepository;

    @Mock
    private SafetyChecker safetyChecker;

    @Mock
    private RewriteProperties rewriteProperties;

    private RewriteServiceImpl service;

    private static final String DEVICE_ID = "device-abc-123";

    // 标准 LLM 响应（Claude 应原样输出的 JSON）
    private static final String VALID_LLM_RESPONSE = """
            {"gentle":{"content":"我最近一直在想我们的事，想和你好好聊聊。","risk_level":"low","risk_reason":"措辞温和，情绪稳定","send_recommended":true,"confidence":0.88},"direct":{"content":"我希望我们能当面把问题谈清楚。","risk_level":"medium","risk_reason":"有直接诉求，对方可能有压力","send_recommended":true,"confidence":0.82},"brief":{"content":"能给我5分钟吗？","risk_level":"low","risk_reason":"极简低防御","send_recommended":true,"confidence":0.90}}
            """;

    // 带前缀文字的 LLM 响应（容忍偶发格式问题）
    private static final String LLM_RESPONSE_WITH_PREFIX = """
            好的，这是改写结果：
            {"gentle":{"content":"温和版内容","risk_level":"low","risk_reason":"措辞温和","send_recommended":true,"confidence":0.85},"direct":{"content":"直接版内容","risk_level":"medium","risk_reason":"有压力感","send_recommended":true,"confidence":0.80},"brief":{"content":"简短版","risk_level":"low","risk_reason":"简短低防御","send_recommended":true,"confidence":0.88}}
            """;

    @BeforeEach
    void setUp() {
        lenient().when(rewriteProperties.getSystemPrompt()).thenReturn("test-rewrite-prompt");
        service = new RewriteServiceImpl(rewriteRepository, llmGateway, dailyUsageRepository, safetyChecker, rewriteProperties);
        // 默认所有内容安全（lenient：部分测试直接调用 parseVariants/checkDailyLimit，不经过 check）
        lenient().when(safetyChecker.check(anyString())).thenReturn(SafetyResult.pass());
    }

    // ── 正常流程 ──────────────────────────────────────────────────────────────

    @Test
    void rewrite_returns_three_variants_with_correct_fields() {
        stubLlm(VALID_LLM_RESPONSE);
        stubSave();

        RewriteResp resp = service.rewrite(buildReq("你为什么要这样对我，我真的好失望"));

        assertThat(resp.getVariants()).hasSize(3);

        RewriteResp.VariantResp gentle = findVariant(resp, "gentle");
        assertThat(gentle.getContent()).isEqualTo("我最近一直在想我们的事，想和你好好聊聊。");
        assertThat(gentle.getRiskLevel()).isEqualTo("low");
        assertThat(gentle.getSendRecommended()).isTrue();
        assertThat(gentle.getConfidence()).isEqualTo(0.88);

        RewriteResp.VariantResp direct = findVariant(resp, "direct");
        assertThat(direct.getRiskLevel()).isEqualTo("medium");

        RewriteResp.VariantResp brief = findVariant(resp, "brief");
        assertThat(brief.getContent()).isEqualTo("能给我5分钟吗？");
    }

    @Test
    void rewrite_saves_record_to_repository() {
        stubLlm(VALID_LLM_RESPONSE);
        stubSave();

        service.rewrite(buildReq("原始消息内容"));

        ArgumentCaptor<RewriteRecord> captor = ArgumentCaptor.forClass(RewriteRecord.class);
        verify(rewriteRepository).save(captor.capture());

        RewriteRecord saved = captor.getValue();
        assertThat(saved.getOriginalMessage()).isEqualTo("原始消息内容");
        assertThat(saved.getVariants()).hasSize(3);
        assertThat(saved.getVariants())
                .extracting(RewriteVariant::getVersion)
                .containsExactly("gentle", "direct", "brief");
    }

    @Test
    void rewrite_passes_original_message_to_llm() {
        stubLlm(VALID_LLM_RESPONSE);
        stubSave();

        service.rewrite(buildReq("你为什么不回我消息"));

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmGateway).complete(anyString(), userPromptCaptor.capture(), any(RoutingContext.class));
        assertThat(userPromptCaptor.getValue()).contains("你为什么不回我消息");
    }

    // ── 防御性规则：high 风险强制 send_recommended=false ─────────────────────

    @Test
    void rewrite_forces_send_recommended_false_when_risk_level_is_high() {
        String llmWithHighRisk = """
                {"gentle":{"content":"温和版","risk_level":"low","risk_reason":"低风险","send_recommended":true,"confidence":0.85},
                 "direct":{"content":"直接版","risk_level":"high","risk_reason":"可能激化冲突","send_recommended":true,"confidence":0.70},
                 "brief":{"content":"简短版","risk_level":"low","risk_reason":"低防御","send_recommended":true,"confidence":0.88}}
                """;

        stubLlm(llmWithHighRisk);
        stubSave();

        RewriteResp resp = service.rewrite(buildReq("你根本不爱我！"));

        RewriteResp.VariantResp direct = findVariant(resp, "direct");
        assertThat(direct.getRiskLevel()).isEqualTo("high");
        assertThat(direct.getSendRecommended()).isFalse();
    }

    // ── 容忍 LLM 前缀文字 ─────────────────────────────────────────────────────

    @Test
    void rewrite_tolerates_llm_response_with_prefix_text() {
        stubLlm(LLM_RESPONSE_WITH_PREFIX);
        stubSave();

        RewriteResp resp = service.rewrite(buildReq("原始消息"));

        assertThat(resp.getVariants()).hasSize(3);
        assertThat(findVariant(resp, "gentle").getContent()).isEqualTo("温和版内容");
    }

    // ── parseVariants 单独覆盖 ────────────────────────────────────────────────

    @Test
    void parseVariants_throws_when_llm_returns_malformed_json() {
        assertThatThrownBy(() -> service.parseVariants("这不是JSON"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("AI 服务暂时不可用");
    }

    @Test
    void parseVariants_throws_when_llm_returns_empty_string() {
        assertThatThrownBy(() -> service.parseVariants(""))
                .isInstanceOf(ApiException.class);
    }

    // ── LLM 调用失败透传 ──────────────────────────────────────────────────────

    @Test
    void rewrite_propagates_exception_when_llm_call_fails() {
        when(llmGateway.complete(anyString(), anyString(), any(RoutingContext.class)))
                .thenThrow(new ApiException(
                        com.kingman.companion.framework.common.CodeEnum.AI_SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.rewrite(buildReq("测试消息")))
                .isInstanceOf(ApiException.class);

        verify(rewriteRepository, never()).save(any());
    }

    // ── 免费层每日限额 ────────────────────────────────────────────────────────

    @Test
    void rewrite_skips_limit_check_when_no_device_id() {
        stubLlm(VALID_LLM_RESPONSE);
        stubSave();

        // 无 deviceId，不应触碰 dailyUsageRepository
        service.rewrite(buildReq("无设备ID的请求"));

        verify(dailyUsageRepository, never()).findByDeviceIdAndUsageDate(any(), any());
    }

    @Test
    void rewrite_records_usage_after_success_with_device_id() {
        stubNoUsageToday();
        stubLlm(VALID_LLM_RESPONSE);
        stubSave();

        service.rewrite(buildReqWithDevice("今日首次改写", DEVICE_ID));

        ArgumentCaptor<RewriteDailyUsage> captor = ArgumentCaptor.forClass(RewriteDailyUsage.class);
        verify(dailyUsageRepository).save(captor.capture());

        RewriteDailyUsage saved = captor.getValue();
        assertThat(saved.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(saved.getUsageDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getCount()).isEqualTo(1);
    }

    @Test
    void rewrite_increments_count_when_existing_usage_found() {
        RewriteDailyUsage existing = buildUsage(DEVICE_ID, LocalDate.now(), 0);
        when(dailyUsageRepository.findByDeviceIdAndUsageDate(DEVICE_ID, LocalDate.now()))
                .thenReturn(Optional.of(existing));
        stubLlm(VALID_LLM_RESPONSE);
        stubSave();

        service.rewrite(buildReqWithDevice("再次改写", DEVICE_ID));

        verify(dailyUsageRepository).save(argThat(u -> u.getCount() == 1));
    }

    @Test
    void rewrite_throws_when_daily_limit_reached() {
        RewriteDailyUsage exhausted = buildUsage(DEVICE_ID, LocalDate.now(), RewriteServiceImpl.DAILY_REWRITE_LIMIT);
        when(dailyUsageRepository.findByDeviceIdAndUsageDate(DEVICE_ID, LocalDate.now()))
                .thenReturn(Optional.of(exhausted));

        assertThatThrownBy(() -> service.rewrite(buildReqWithDevice("超限改写", DEVICE_ID)))
                .isInstanceOf(ApiException.class);

        // 超限时不调用 LLM，不写入记录
        verify(llmGateway, never()).complete(any(), any(), any());
        verify(rewriteRepository, never()).save(any());
    }

    @Test
    void rewrite_does_not_record_usage_when_llm_fails() {
        stubNoUsageToday();
        when(llmGateway.complete(anyString(), anyString(), any(RoutingContext.class)))
                .thenThrow(new ApiException(
                        com.kingman.companion.framework.common.CodeEnum.AI_SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.rewrite(buildReqWithDevice("测试消息", DEVICE_ID)))
                .isInstanceOf(ApiException.class);

        // LLM 失败时不记录使用（避免扣除免费次数）
        verify(dailyUsageRepository, never()).save(any());
    }

    // ── 安全检测 ──────────────────────────────────────────────────────────────

    @Test
    void rewrite_throws_SafetyBlockedException_when_content_is_unsafe() {
        when(safetyChecker.check(anyString())).thenReturn(SafetyResult.block("self_harm"));

        assertThatThrownBy(() -> service.rewrite(buildReq("我想自杀因为他不爱我了")))
                .isInstanceOf(SafetyBlockedException.class)
                .extracting(e -> ((SafetyBlockedException) e).getTriggerType())
                .isEqualTo("self_harm");

        // 安全拦截后不调用 LLM，不写记录
        verify(llmGateway, never()).complete(any(), any(), any());
        verify(rewriteRepository, never()).save(any());
    }

    @Test
    void rewrite_checks_safety_before_limit_check() {
        // 即使 deviceId 存在也应先触发安全检测
        when(safetyChecker.check(anyString())).thenReturn(SafetyResult.block("violence_threat"));

        assertThatThrownBy(() -> service.rewrite(buildReqWithDevice("危险内容", DEVICE_ID)))
                .isInstanceOf(SafetyBlockedException.class);

        verify(dailyUsageRepository, never()).findByDeviceIdAndUsageDate(any(), any());
    }

    // ── checkDailyLimit 单元覆盖 ──────────────────────────────────────────────

    @Test
    void checkDailyLimit_passes_when_no_record_exists() {
        when(dailyUsageRepository.findByDeviceIdAndUsageDate(DEVICE_ID, LocalDate.now()))
                .thenReturn(Optional.empty());

        assertThatCode(() -> service.checkDailyLimit(DEVICE_ID)).doesNotThrowAnyException();
    }

    @Test
    void checkDailyLimit_passes_when_count_below_limit() {
        when(dailyUsageRepository.findByDeviceIdAndUsageDate(DEVICE_ID, LocalDate.now()))
                .thenReturn(Optional.of(buildUsage(DEVICE_ID, LocalDate.now(), 0)));

        assertThatCode(() -> service.checkDailyLimit(DEVICE_ID)).doesNotThrowAnyException();
    }

    @Test
    void checkDailyLimit_throws_when_count_equals_limit() {
        when(dailyUsageRepository.findByDeviceIdAndUsageDate(DEVICE_ID, LocalDate.now()))
                .thenReturn(Optional.of(buildUsage(DEVICE_ID, LocalDate.now(), RewriteServiceImpl.DAILY_REWRITE_LIMIT)));

        assertThatThrownBy(() -> service.checkDailyLimit(DEVICE_ID))
                .isInstanceOf(ApiException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubLlm(String response) {
        when(llmGateway.complete(anyString(), anyString(), any(RoutingContext.class))).thenReturn(response);
    }

    private void stubSave() {
        when(rewriteRepository.save(any(RewriteRecord.class))).thenAnswer(inv -> {
            RewriteRecord r = inv.getArgument(0);
            r.setCreateTime(LocalDateTime.of(2026, 4, 30, 10, 0, 0));
            return r;
        });
    }

    private void stubNoUsageToday() {
        when(dailyUsageRepository.findByDeviceIdAndUsageDate(eq(DEVICE_ID), any(LocalDate.class)))
                .thenReturn(Optional.empty());
    }

    private RewriteReq buildReq(String message) {
        RewriteReq req = new RewriteReq();
        req.setOriginalMessage(message);
        return req;
    }

    private RewriteReq buildReqWithDevice(String message, String deviceId) {
        RewriteReq req = buildReq(message);
        req.setDeviceId(deviceId);
        return req;
    }

    private RewriteDailyUsage buildUsage(String deviceId, LocalDate date, int count) {
        RewriteDailyUsage u = new RewriteDailyUsage();
        u.setDeviceId(deviceId);
        u.setUsageDate(date);
        u.setCount(count);
        return u;
    }

    private RewriteResp.VariantResp findVariant(RewriteResp resp, String version) {
        return resp.getVariants().stream()
                .filter(v -> version.equals(v.getVersion()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("variant not found: " + version));
    }
}
