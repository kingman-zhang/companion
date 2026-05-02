package com.kingman.companion.module.assessment.service.impl;

import com.kingman.companion.component.enums.*;
import com.kingman.companion.component.llm.LlmGateway;
import com.kingman.companion.component.llm.RoutingContext;
import com.kingman.companion.framework.exception.ApiException;
import com.kingman.companion.module.assessment.engine.ScoreEngine;
import com.kingman.companion.module.assessment.entity.Assessment;
import com.kingman.companion.module.assessment.repository.AssessmentRepository;
import com.kingman.companion.module.assessment.req.AssessmentReq;
import com.kingman.companion.module.assessment.resp.AssessmentResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P1 验收测试：AssessmentServiceImpl LLM 增强接入
 */
@ExtendWith(MockitoExtension.class)
class AssessmentServiceImplTest {

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private LlmGateway llmGateway;

    private AssessmentServiceImpl service;
    private ScoreEngine scoreEngine;

    private static final String VALID_LLM_RESPONSE =
            "{\"core_insight\":\"沟通断裂是核心\",\"llm_reason\":\"从评估结果来看，你们的情感基础尚存，但沟通质量是最大的薄弱环节。建议先给彼此一周冷静期，再用平和的方式表达你的感受。\"}";

    @BeforeEach
    void setUp() {
        scoreEngine = new ScoreEngine();
        service = new AssessmentServiceImpl(assessmentRepository, scoreEngine, llmGateway);
    }

    // ── 正常流程 ──────────────────────────────────────────────────────────────

    @Test
    void submit_uses_llm_insight_and_reason_when_llm_succeeds() {
        when(llmGateway.complete(anyString(), anyString(), any(RoutingContext.class))).thenReturn(VALID_LLM_RESPONSE);
        stubSave();

        AssessmentResp resp = service.submit(buildFullReq());

        assertThat(resp.getCoreInsight()).isEqualTo("沟通断裂是核心");
        assertThat(resp.getLlmReason()).contains("沟通质量是最大的薄弱环节");
    }

    @Test
    void submit_passes_score_context_to_llm() {
        when(llmGateway.complete(anyString(), anyString(), any(RoutingContext.class))).thenReturn(VALID_LLM_RESPONSE);
        stubSave();

        service.submit(buildFullReq());

        ArgumentCaptor<String> userMsgCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmGateway).complete(eq(AssessmentServiceImpl.SYSTEM_PROMPT), userMsgCaptor.capture(), any(RoutingContext.class));

        String prompt = userMsgCaptor.getValue();
        assertThat(prompt).contains("评估等级");
        assertThat(prompt).contains("情感联结得分");
        assertThat(prompt).contains("沟通质量得分");
    }

    @Test
    void submit_saves_assessment_to_repository() {
        when(llmGateway.complete(anyString(), anyString(), any(RoutingContext.class))).thenReturn(VALID_LLM_RESPONSE);
        stubSave();

        service.submit(buildFullReq());

        ArgumentCaptor<Assessment> captor = ArgumentCaptor.forClass(Assessment.class);
        verify(assessmentRepository).save(captor.capture());

        Assessment saved = captor.getValue();
        assertThat(saved.getScore()).isPositive();
        assertThat(saved.getLevel()).isNotNull();
        assertThat(saved.getCoreInsight()).isEqualTo("沟通断裂是核心");
    }

    // ── LLM 失败降级 ──────────────────────────────────────────────────────────

    @Test
    void submit_falls_back_to_rule_template_when_llm_throws() {
        when(llmGateway.complete(anyString(), anyString(), any(RoutingContext.class)))
                .thenThrow(new ApiException(
                        com.kingman.companion.framework.common.CodeEnum.AI_SERVICE_UNAVAILABLE));
        stubSave();

        // LLM 失败不应让整个 submit 抛异常
        AssessmentResp resp = service.submit(buildFullReq());

        // 验证 fallback 文字非空（来自 ScoreEngine 模板）
        assertThat(resp.getCoreInsight()).isNotBlank();
        assertThat(resp.getLlmReason()).isNotBlank();
    }

    @Test
    void submit_falls_back_when_llm_returns_invalid_json() {
        when(llmGateway.complete(anyString(), anyString(), any(RoutingContext.class))).thenReturn("这不是JSON");
        stubSave();

        AssessmentResp resp = service.submit(buildFullReq());

        // 降级后内容来自规则引擎，必须非空
        assertThat(resp.getCoreInsight()).isNotBlank();
        assertThat(resp.getLlmReason()).isNotBlank();
    }

    // ── llmEnrich 单元覆盖 ────────────────────────────────────────────────────

    @Test
    void llmEnrich_returns_llm_result_on_success() {
        when(llmGateway.complete(anyString(), anyString(), any(RoutingContext.class))).thenReturn(VALID_LLM_RESPONSE);

        ScoreEngine.ScoreResult score = scoreEngine.calculate(buildFullReq());
        AssessmentServiceImpl.LlmEnrichment enrichment = service.llmEnrich(score, buildFullReq());

        assertThat(enrichment.coreInsight()).isEqualTo("沟通断裂是核心");
        assertThat(enrichment.llmReason()).isNotBlank();
    }

    @Test
    void llmEnrich_returns_fallback_on_llm_failure() {
        when(llmGateway.complete(anyString(), anyString(), any(RoutingContext.class)))
                .thenThrow(new RuntimeException("network error"));

        ScoreEngine.ScoreResult score = scoreEngine.calculate(buildFullReq());
        AssessmentServiceImpl.LlmEnrichment enrichment = service.llmEnrich(score, buildFullReq());

        // 降级到规则模板
        assertThat(enrichment.coreInsight()).isEqualTo(score.coreInsight());
        assertThat(enrichment.llmReason()).isEqualTo(score.reason());
    }

    // ── parseLlmEnrichment 单元覆盖 ───────────────────────────────────────────

    @Test
    void parseLlmEnrichment_handles_prefix_text() {
        String withPrefix = "好的，这是分析：\n" + VALID_LLM_RESPONSE;
        AssessmentServiceImpl.LlmEnrichment result = service.parseLlmEnrichment(withPrefix);

        assertThat(result.coreInsight()).isEqualTo("沟通断裂是核心");
    }

    @Test
    void parseLlmEnrichment_throws_on_empty_string() {
        assertThatThrownBy(() -> service.parseLlmEnrichment(""))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void parseLlmEnrichment_throws_on_malformed_json() {
        assertThatThrownBy(() -> service.parseLlmEnrichment("这不是JSON"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void parseLlmEnrichment_throws_when_fields_are_blank() {
        String json = "{\"core_insight\":\"\",\"llm_reason\":\"\"}";
        assertThatThrownBy(() -> service.parseLlmEnrichment(json))
                .isInstanceOf(ApiException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubSave() {
        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(inv -> {
            Assessment a = inv.getArgument(0);
            a.setCreateTime(LocalDateTime.of(2026, 4, 30, 10, 0, 0));
            return a;
        });
    }

    private AssessmentReq buildFullReq() {
        AssessmentReq req = new AssessmentReq();
        req.setRelationshipDuration(RelationshipDuration.SIX_MONTHS_TO_2Y);
        req.setBreakupMethod(BreakupMethod.DURING_ARGUMENT);
        req.setCurrentEmotion(CurrentEmotion.ANGRY);
        req.setCommunicationQuality(CommunicationQuality.FREQUENT_CONFLICT);
        req.setConflictStyle(ConflictStyle.AVOID_THEN_IGNORE);
        req.setPartnerLovePerception(PartnerLovePerception.UNSURE_CHANGED);
        req.setUserPrimaryIntent(UserPrimaryIntent.RECONCILE);
        return req;
    }
}
