package com.kingman.companion.component.safety;

import com.kingman.companion.framework.exception.SafetyBlockedException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内容安全检测器（MVP：关键词规则）
 *
 * <p>覆盖四类风险：自伤/自杀、暴力威胁、家暴、跟踪控制。
 * 任意一类命中即返回对应 triggerType，调用方通过 {@link SafetyResult#throwIfBlocked()} 抛出
 * {@link SafetyBlockedException}（HTTP 451）。
 *
 * <p>规则在方法内以常量定义，未来可升级为 LLM 检测，不改接口。
 */
@Component
public class SafetyChecker {

    // ── 自伤 / 自杀 ────────────────────────────────────────────────────────
    private static final List<String> SELF_HARM_KEYWORDS = List.of(
            "自杀", "轻生", "不想活", "活不下去", "想死", "去死",
            "了结自己", "消失算了", "死了算了", "割腕", "跳楼", "跳桥",
            "上吊", "吃药死", "自伤", "伤害自己"
    );

    // ── 暴力威胁 ──────────────────────────────────────────────────────────
    private static final List<String> VIOLENCE_THREAT_KEYWORDS = List.of(
            "打死你", "杀了你", "弄死你", "灭了你", "让你消失",
            "砍你", "拿刀", "拿枪", "报复你", "你死定了"
    );

    // ── 家暴 / 人身伤害 ───────────────────────────────────────────────────
    private static final List<String> DOMESTIC_VIOLENCE_KEYWORDS = List.of(
            "家暴", "打我", "打人", "被打", "殴打", "踢我",
            "掐我", "扇我", "伤害我", "身体伤害"
    );

    // ── 跟踪 / 控制 ───────────────────────────────────────────────────────
    private static final List<String> STALKING_KEYWORDS = List.of(
            "跟踪我", "监视我", "监控我", "不让我出门", "锁我在",
            "强迫我", "威胁我", "控制我", "不许我"
    );

    /**
     * 对输入内容执行安全检测。
     *
     * @param content 用户输入的原始文本
     * @return {@link SafetyResult}，安全时 {@code isSafe() == true}，否则含 triggerType
     */
    public SafetyResult check(String content) {
        if (content == null || content.isBlank()) {
            return SafetyResult.pass();
        }

        if (containsAny(content, SELF_HARM_KEYWORDS)) {
            return SafetyResult.block("self_harm");
        }
        if (containsAny(content, VIOLENCE_THREAT_KEYWORDS)) {
            return SafetyResult.block("violence_threat");
        }
        if (containsAny(content, DOMESTIC_VIOLENCE_KEYWORDS)) {
            return SafetyResult.block("domestic_violence");
        }
        if (containsAny(content, STALKING_KEYWORDS)) {
            return SafetyResult.block("stalking_control");
        }

        return SafetyResult.pass();
    }

    private boolean containsAny(String content, List<String> keywords) {
        for (String kw : keywords) {
            if (content.contains(kw)) return true;
        }
        return false;
    }
}
