package com.kingman.companion.component.llm;

import com.kingman.companion.component.safety.SafetyLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ModelRouter 路由决策单元测试
 */
@ExtendWith(MockitoExtension.class)
class ModelRouterTest {

    @Mock
    private RouterProperties properties;

    private ModelRouter router;

    @BeforeEach
    void setUp() {
        // lenient：SAFETY 覆盖测试会提前返回，不会访问 longContextThreshold
        lenient().when(properties.getLongContextThreshold()).thenReturn(2000);
        router = new ModelRouter(properties);
    }

    // ── SAFETY 覆盖（最高优先级）────────────────────────────────────────────

    @Test
    void route_returns_SAFETY_when_safetyLevel_is_CONCERNING() {
        RoutingContext ctx = new RoutingContext(ModelTier.LITE, 100, SafetyLevel.CONCERNING);
        assertThat(router.route(ctx)).isEqualTo(ModelTier.SAFETY);
    }

    @Test
    void route_returns_SAFETY_even_when_requestedTier_is_ADVANCED_and_level_is_CONCERNING() {
        RoutingContext ctx = new RoutingContext(ModelTier.ADVANCED, 5000, SafetyLevel.CONCERNING);
        assertThat(router.route(ctx)).isEqualTo(ModelTier.SAFETY);
    }

    // ── 长上下文上调（次优先级）──────────────────────────────────────────────

    @Test
    void route_returns_LONG_CONTEXT_when_input_exceeds_threshold() {
        when(properties.getLongContextThreshold()).thenReturn(2000);
        RoutingContext ctx = new RoutingContext(ModelTier.LITE, 2001, SafetyLevel.SAFE);
        assertThat(router.route(ctx)).isEqualTo(ModelTier.LONG_CONTEXT);
    }

    @Test
    void route_returns_requestedTier_when_input_equals_threshold() {
        when(properties.getLongContextThreshold()).thenReturn(2000);
        RoutingContext ctx = new RoutingContext(ModelTier.LITE, 2000, SafetyLevel.SAFE);
        assertThat(router.route(ctx)).isEqualTo(ModelTier.LITE);
    }

    // ── 正常路由（按 requestedTier）─────────────────────────────────────────

    @Test
    void route_returns_LITE_for_normal_chat() {
        when(properties.getLongContextThreshold()).thenReturn(2000);
        RoutingContext ctx = RoutingContext.chat(100, SafetyLevel.SAFE);
        assertThat(router.route(ctx)).isEqualTo(ModelTier.LITE);
    }

    @Test
    void route_returns_ADVANCED_for_deep_analysis() {
        when(properties.getLongContextThreshold()).thenReturn(2000);
        RoutingContext ctx = RoutingContext.deepAnalysis(200, SafetyLevel.SAFE);
        assertThat(router.route(ctx)).isEqualTo(ModelTier.ADVANCED);
    }

    @Test
    void route_returns_STANDARD_for_standard_context() {
        when(properties.getLongContextThreshold()).thenReturn(2000);
        RoutingContext ctx = RoutingContext.standard();
        assertThat(router.route(ctx)).isEqualTo(ModelTier.STANDARD);
    }

    // ── BLOCKED 不触发 SAFETY tier（BLOCKED 在 SafetyChecker 层已阻断）────────

    @Test
    void route_does_not_reroute_BLOCKED_to_SAFETY() {
        when(properties.getLongContextThreshold()).thenReturn(2000);
        // BLOCKED 内容被 throwIfBlocked() 拦截，不会到达 router
        // 但若万一传入，router 应按 requestedTier 处理（BLOCKED != CONCERNING）
        RoutingContext ctx = new RoutingContext(ModelTier.LITE, 100, SafetyLevel.BLOCKED);
        assertThat(router.route(ctx)).isEqualTo(ModelTier.LITE);
    }
}
