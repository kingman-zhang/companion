package com.kingman.companion.component.llm;

import com.kingman.companion.component.safety.SafetyLevel;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LlmGatewayImpl 路由 + fallback + 安全提示注入单元测试
 *
 * <p>Fallback 是模型级别的（同一 provider，不同 modelId），不是 provider 级别。
 * 因此 providerA("anthropic") 处理所有 "anthropic" 配置的 fallback 链。
 * providerB 在此测试中代表 "openai" provider，用于测试跳过不支持的 provider。
 */
@ExtendWith(MockitoExtension.class)
class LlmGatewayImplTest {

    @Mock
    private ModelRouter router;

    @Mock
    private RouterProperties properties;

    /** Anthropic provider */
    @Mock
    private LlmProvider providerA;

    /** OpenAI provider（用于测试 provider 跳过逻辑） */
    @Mock
    private LlmProvider providerB;

    private LlmGatewayImpl gateway;

    private static final ModelConfig HAIKU = new ModelConfig("anthropic", "claude-haiku-4-5", 1024, 30);
    private static final ModelConfig SONNET = new ModelConfig("anthropic", "claude-sonnet-4-6", 2048, 60);
    private static final ModelConfig GPT4O = new ModelConfig("openai", "gpt-4o", 1024, 30);

    private static final String SYSTEM = "系统提示";
    private static final List<LlmMessage> MESSAGES = List.of(LlmMessage.user("用户输入"));

    @BeforeEach
    void setUp() {
        // providerA 支持 anthropic，providerB 支持 openai
        lenient().when(providerA.supports("anthropic")).thenReturn(true);
        lenient().when(providerA.supports("openai")).thenReturn(false);
        lenient().when(providerB.supports("openai")).thenReturn(true);
        lenient().when(providerB.supports("anthropic")).thenReturn(false);

        gateway = new LlmGatewayImpl(router, properties, List.of(providerA, providerB));
    }

    // ── 正常路由 ──────────────────────────────────────────────────────────────

    @Test
    void completeWithHistory_calls_first_model_in_chain() throws Exception {
        when(router.route(any())).thenReturn(ModelTier.LITE);
        when(properties.getChain(ModelTier.LITE)).thenReturn(List.of(HAIKU));
        when(providerA.call(anyString(), anyList(), eq(HAIKU))).thenReturn("AI回复");

        String result = gateway.completeWithHistory(SYSTEM, MESSAGES, RoutingContext.chat(100, SafetyLevel.SAFE));

        assertThat(result).isEqualTo("AI回复");
        verify(providerA).call(SYSTEM, MESSAGES, HAIKU);
    }

    // ── Fallback：模型级别（同 provider，不同 modelId）───────────────────────

    @Test
    void completeWithHistory_falls_back_to_second_model_when_first_fails() throws Exception {
        when(router.route(any())).thenReturn(ModelTier.STANDARD);
        when(properties.getChain(ModelTier.STANDARD)).thenReturn(List.of(HAIKU, SONNET));
        // 第一个模型（HAIKU）失败
        when(providerA.call(anyString(), anyList(), eq(HAIKU)))
                .thenThrow(new RuntimeException("timeout"));
        // 第二个模型（SONNET）成功
        when(providerA.call(anyString(), anyList(), eq(SONNET))).thenReturn("备用回复");

        String result = gateway.completeWithHistory(SYSTEM, MESSAGES, RoutingContext.standard());

        assertThat(result).isEqualTo("备用回复");
        verify(providerA).call(anyString(), anyList(), eq(HAIKU));
        verify(providerA).call(anyString(), anyList(), eq(SONNET));
    }

    @Test
    void completeWithHistory_throws_AI_SERVICE_UNAVAILABLE_when_all_models_fail() throws Exception {
        when(router.route(any())).thenReturn(ModelTier.STANDARD);
        when(properties.getChain(ModelTier.STANDARD)).thenReturn(List.of(HAIKU, SONNET));
        when(providerA.call(anyString(), anyList(), eq(HAIKU))).thenThrow(new RuntimeException("fail A"));
        when(providerA.call(anyString(), anyList(), eq(SONNET))).thenThrow(new RuntimeException("fail B"));

        assertThatThrownBy(() ->
                gateway.completeWithHistory(SYSTEM, MESSAGES, RoutingContext.standard()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCodeEnum())
                .isEqualTo(CodeEnum.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    void completeWithHistory_throws_when_chain_is_empty() {
        when(router.route(any())).thenReturn(ModelTier.LITE);
        when(properties.getChain(ModelTier.LITE)).thenReturn(List.of());

        assertThatThrownBy(() ->
                gateway.completeWithHistory(SYSTEM, MESSAGES, RoutingContext.chat(100, SafetyLevel.SAFE)))
                .isInstanceOf(ApiException.class);
    }

    // ── SAFETY tier：注入安全提示前缀 ────────────────────────────────────────

    @Test
    void completeWithHistory_injects_safety_prefix_when_tier_is_SAFETY() throws Exception {
        when(router.route(any())).thenReturn(ModelTier.SAFETY);
        when(properties.getChain(ModelTier.SAFETY)).thenReturn(List.of(HAIKU));
        when(providerA.call(anyString(), anyList(), any())).thenReturn("安全回复");

        gateway.completeWithHistory(SYSTEM, MESSAGES,
                new RoutingContext(ModelTier.LITE, 100, SafetyLevel.CONCERNING));

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(providerA).call(systemCaptor.capture(), anyList(), any());

        String effectiveSystem = systemCaptor.getValue();
        assertThat(effectiveSystem).startsWith("【安全注意");
        assertThat(effectiveSystem).contains(SYSTEM);
    }

    @Test
    void completeWithHistory_does_not_inject_safety_prefix_for_non_safety_tier() throws Exception {
        when(router.route(any())).thenReturn(ModelTier.LITE);
        when(properties.getChain(ModelTier.LITE)).thenReturn(List.of(HAIKU));
        when(providerA.call(anyString(), anyList(), any())).thenReturn("普通回复");

        gateway.completeWithHistory(SYSTEM, MESSAGES, RoutingContext.chat(100, SafetyLevel.SAFE));

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(providerA).call(systemCaptor.capture(), anyList(), any());
        assertThat(systemCaptor.getValue()).isEqualTo(SYSTEM);
    }

    // ── 跳过无法匹配 provider 的 ModelConfig ──────────────────────────────────

    @Test
    void completeWithHistory_skips_config_when_no_provider_supports_it() throws Exception {
        // 链中第一个是 openai 模型（没有任何 LlmProvider 支持它），第二个是 anthropic
        // providerA: supports anthropic only; providerB: supports openai only
        // 但 gateway 的 providers 列表只有 providerA（只支持 anthropic），模拟没有 openai provider
        LlmGatewayImpl anthroOnlyGateway = new LlmGatewayImpl(router, properties, List.of(providerA));

        when(router.route(any())).thenReturn(ModelTier.STANDARD);
        when(properties.getChain(ModelTier.STANDARD)).thenReturn(List.of(GPT4O, SONNET));
        when(providerA.call(anyString(), anyList(), eq(SONNET))).thenReturn("Anthropic备用回复");

        String result = anthroOnlyGateway.completeWithHistory(SYSTEM, MESSAGES, RoutingContext.standard());

        assertThat(result).isEqualTo("Anthropic备用回复");
        // openai 配置没有匹配的 provider → 被跳过，不调用任何 call
        verify(providerA, never()).call(anyString(), anyList(), eq(GPT4O));
        verify(providerA).call(anyString(), anyList(), eq(SONNET));
    }
}
