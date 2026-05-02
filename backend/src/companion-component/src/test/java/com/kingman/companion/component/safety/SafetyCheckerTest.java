package com.kingman.companion.component.safety;

import com.kingman.companion.framework.exception.SafetyBlockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * SafetyChecker 关键词检测单元测试
 */
class SafetyCheckerTest {

    private SafetyChecker checker;

    @BeforeEach
    void setUp() {
        checker = new SafetyChecker();
    }

    // ── 安全内容 ──────────────────────────────────────────────────────────────

    @Test
    void check_returns_safe_for_normal_emotional_content() {
        assertThat(checker.check("我好难过，他为什么不回我消息").safe()).isTrue();
    }

    @Test
    void check_returns_safe_for_blank_content() {
        assertThat(checker.check("").safe()).isTrue();
        assertThat(checker.check("   ").safe()).isTrue();
        assertThat(checker.check(null).safe()).isTrue();
    }

    // ── 自伤 / 自杀 ───────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"我想自杀", "活不下去了", "不想活了", "割腕", "跳楼", "想死"})
    void check_detects_self_harm(String content) {
        SafetyResult result = checker.check(content);
        assertThat(result.safe()).isFalse();
        assertThat(result.triggerType()).isEqualTo("self_harm");
    }

    @Test
    void check_detects_self_harm_embedded_in_sentence() {
        SafetyResult result = checker.check("我真的好痛苦，有时候感觉活不下去了，你懂吗");
        assertThat(result.safe()).isFalse();
        assertThat(result.triggerType()).isEqualTo("self_harm");
    }

    // ── 暴力威胁 ──────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"我要打死你", "杀了你", "弄死你", "拿刀"})
    void check_detects_violence_threat(String content) {
        SafetyResult result = checker.check(content);
        assertThat(result.safe()).isFalse();
        assertThat(result.triggerType()).isEqualTo("violence_threat");
    }

    // ── 家暴 ─────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"他家暴我", "他打我", "被打了", "他殴打我"})
    void check_detects_domestic_violence(String content) {
        SafetyResult result = checker.check(content);
        assertThat(result.safe()).isFalse();
        assertThat(result.triggerType()).isEqualTo("domestic_violence");
    }

    // ── 跟踪 / 控制 ───────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"他在跟踪我", "他监视我", "他不让我出门", "他锁我在家", "他控制我"})
    void check_detects_stalking_control(String content) {
        SafetyResult result = checker.check(content);
        assertThat(result.safe()).isFalse();
        assertThat(result.triggerType()).isEqualTo("stalking_control");
    }

    // ── SafetyResult.throwIfBlocked ───────────────────────────────────────────

    @Test
    void throwIfBlocked_throws_SafetyBlockedException_when_not_safe() {
        SafetyResult blocked = SafetyResult.block("self_harm");
        assertThatThrownBy(blocked::throwIfBlocked)
                .isInstanceOf(SafetyBlockedException.class)
                .extracting(e -> ((SafetyBlockedException) e).getTriggerType())
                .isEqualTo("self_harm");
    }

    @Test
    void throwIfBlocked_does_nothing_when_safe() {
        assertThatCode(() -> SafetyResult.pass().throwIfBlocked()).doesNotThrowAnyException();
    }

    @Test
    void throwIfBlocked_does_nothing_when_concerning() {
        assertThatCode(() -> SafetyResult.concerning("passive_ideation").throwIfBlocked()).doesNotThrowAnyException();
    }

    // ── CONCERNING 级别：被动/模糊表达 ───────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"想消失", "活得好累", "不想撑了", "撑不下去", "没意思", "心好累"})
    void check_returns_concerning_for_passive_ideation(String content) {
        SafetyResult result = checker.check(content);
        assertThat(result.level()).isEqualTo(SafetyLevel.CONCERNING);
        assertThat(result.triggerType()).isEqualTo("passive_ideation");
    }

    @ParameterizedTest
    @ValueSource(strings = {"崩溃了", "喘不过气", "走不下去了", "看不到希望", "失去一切"})
    void check_returns_concerning_for_emotional_crisis(String content) {
        SafetyResult result = checker.check(content);
        assertThat(result.level()).isEqualTo(SafetyLevel.CONCERNING);
        assertThat(result.triggerType()).isEqualTo("emotional_crisis");
    }

    @Test
    void check_prioritizes_blocked_over_concerning() {
        // "想死" is BLOCKED, "心好累" is CONCERNING → should be BLOCKED
        SafetyResult result = checker.check("我心好累，我就想死");
        assertThat(result.level()).isEqualTo(SafetyLevel.BLOCKED);
        assertThat(result.triggerType()).isEqualTo("self_harm");
    }

    // ── 优先级：自伤优先于其他 ────────────────────────────────────────────────

    @Test
    void check_prioritizes_self_harm_over_other_flags() {
        // 同时含自伤 + 暴力威胁关键词
        SafetyResult result = checker.check("我想自杀，也想打死他");
        assertThat(result.triggerType()).isEqualTo("self_harm");
    }
}
