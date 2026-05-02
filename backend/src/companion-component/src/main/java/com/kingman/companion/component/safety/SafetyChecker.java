package com.kingman.companion.component.safety;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内容安全检测器（MVP：关键词规则，三级判定）
 *
 * <p>检测优先级：
 * <ol>
 *   <li>明确、主动的危险内容 → {@link SafetyLevel#BLOCKED}（HTTP 451）</li>
 *   <li>被动、模糊的消极表达 → {@link SafetyLevel#CONCERNING}（路由到 SAFETY 模型）</li>
 *   <li>其余 → {@link SafetyLevel#SAFE}</li>
 * </ol>
 *
 * <p>规则以常量列表定义，未来可无缝替换为 LLM 检测。
 */
@Component
public class SafetyChecker {

    // ── BLOCKED：明确危险，直接 HTTP 451 ─────────────────────────────────────

    private static final List<String> BLOCK_SELF_HARM = List.of(
            "自杀", "割腕", "跳楼", "跳桥", "上吊", "吃药死", "自伤", "想死", "去死",
            "不想活了", "活不下去了"
    );

    private static final List<String> BLOCK_VIOLENCE = List.of(
            "打死你", "杀了你", "弄死你", "灭了你", "让你消失",
            "砍你", "拿刀", "拿枪", "报复你", "你死定了"
    );

    private static final List<String> BLOCK_DOMESTIC_VIOLENCE = List.of(
            "家暴", "打我", "被打", "殴打", "踢我", "掐我", "扇我"
    );

    private static final List<String> BLOCK_STALKING = List.of(
            "跟踪我", "监视我", "监控我", "不让我出门", "锁我在",
            "强迫我", "威胁我", "控制我"
    );

    // ── CONCERNING：被动/模糊，路由到 SAFETY 模型但不阻断 ────────────────────

    private static final List<String> CONCERN_PASSIVE_IDEATION = List.of(
            "想消失", "活得好累", "太累了", "不想撑了", "撑不下去",
            "没意思", "活着有什么意思", "不想面对了", "心好累"
    );

    private static final List<String> CONCERN_EMOTIONAL_CRISIS = List.of(
            "崩溃了", "喘不过气", "走不下去了", "看不到希望",
            "什么都没了", "失去一切"
    );

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * 对用户输入执行三级安全检测。
     *
     * @param content 用户输入文本
     * @return {@link SafetyResult}，包含 {@link SafetyLevel} 和触发类型
     */
    public SafetyResult check(String content) {
        if (content == null || content.isBlank()) {
            return SafetyResult.pass();
        }

        // 第一级：BLOCKED（明确危险）
        if (containsAny(content, BLOCK_SELF_HARM)) {
            return SafetyResult.block("self_harm");
        }
        if (containsAny(content, BLOCK_VIOLENCE)) {
            return SafetyResult.block("violence_threat");
        }
        if (containsAny(content, BLOCK_DOMESTIC_VIOLENCE)) {
            return SafetyResult.block("domestic_violence");
        }
        if (containsAny(content, BLOCK_STALKING)) {
            return SafetyResult.block("stalking_control");
        }

        // 第二级：CONCERNING（被动/模糊）
        if (containsAny(content, CONCERN_PASSIVE_IDEATION)) {
            return SafetyResult.concerning("passive_ideation");
        }
        if (containsAny(content, CONCERN_EMOTIONAL_CRISIS)) {
            return SafetyResult.concerning("emotional_crisis");
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
