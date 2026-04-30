package com.kingman.companion.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.kingman.companion.component.enums.AssessmentLevel;
import com.kingman.companion.component.enums.RecommendedAction;
import com.kingman.companion.component.enums.UserPrimaryIntent;
import com.kingman.companion.framework.common.IResult;
import com.kingman.companion.framework.config.JacksonConfig;
import com.kingman.companion.module.assessment.resp.AssessmentResp;
import com.kingman.companion.module.chat.resp.ChatResp;
import com.kingman.companion.module.rewrite.resp.RewriteResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0 验收测试：LocalDateTime 序列化 / 反序列化格式
 *
 * <p>纯单元测试，无 Spring 上下文依赖。
 * 手动组装与生产环境相同的 ObjectMapper（JacksonConfig customizer + SNAKE_CASE），
 * 验证三个核心 Resp 的 createdAt 字段输出格式符合前端约定：yyyy-MM-dd HH:mm:ss。
 */
class DateTimeSerializationTest {

    private static final LocalDateTime SAMPLE_DT = LocalDateTime.of(2026, 4, 29, 10, 30, 0);
    private static final String EXPECTED_STR     = "2026-04-29 10:30:00";

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 与生产行为保持一致：
        //   1. JacksonConfig.localDateTimeCustomizer() 注册 LocalDateTime 序列化器
        //   2. application.yml property-naming-strategy: SNAKE_CASE
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().localDateTimeCustomizer().customize(builder);
        builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper = builder.build();
    }

    // ── LocalDateTime 基础序列化 ──────────────────────────────────────────

    @Test
    void localDateTime_serializes_to_space_separated_format() throws Exception {
        String json = objectMapper.writeValueAsString(SAMPLE_DT);
        assertThat(json).isEqualTo("\"" + EXPECTED_STR + "\"");
    }

    @Test
    void localDateTime_not_serialized_as_timestamp_array() throws Exception {
        String json = objectMapper.writeValueAsString(SAMPLE_DT);
        // 不应出现 Jackson 默认的数组形式 [2026,4,29,10,30,0]
        assertThat(json).doesNotStartWith("[");
    }

    @Test
    void localDateTime_not_serialized_as_iso8601() throws Exception {
        String json = objectMapper.writeValueAsString(SAMPLE_DT);
        // 不应出现 ISO-8601 的 T 分隔符（如 "2026-04-29T10:30:00"）
        assertThat(json).doesNotContain("T");
    }

    // ── LocalDateTime 反序列化 ─────────────────────────────────────────────

    @Test
    void localDateTime_deserializes_from_space_separated_format() throws Exception {
        LocalDateTime result = objectMapper.readValue(
                "\"" + EXPECTED_STR + "\"", LocalDateTime.class);
        assertThat(result).isEqualTo(SAMPLE_DT);
    }

    // ── AssessmentResp.createdAt ───────────────────────────────────────────

    @Test
    void assessmentResp_createdAt_uses_correct_format() throws Exception {
        AssessmentResp resp = AssessmentResp.builder()
                .assessmentId("test-assessment-id")
                .score(75)
                .level(AssessmentLevel.GREEN)
                .confidence(0.9)
                .emotionalConnectionScore(80)
                .communicationScore(70)
                .conflictScore(75)
                .coreInsight("你们缺的不是感情，是喘息空间。")
                .llmReason("关系处于敏感期，但仍有修复空间。")
                .recommendedAction(RecommendedAction.COOL_DOWN)
                .userPrimaryIntent(UserPrimaryIntent.RECONCILE)
                .createdAt(SAMPLE_DT)
                .build();

        String json = objectMapper.writeValueAsString(resp);

        // 字段名用 snake_case
        assertThat(json).contains("\"assessment_id\"");
        assertThat(json).contains("\"emotional_connection_score\"");
        // 时间格式正确
        assertThat(json).contains("\"created_at\":\"" + EXPECTED_STR + "\"");
    }

    // ── ChatResp.createdAt ─────────────────────────────────────────────────

    @Test
    void chatResp_createdAt_uses_correct_format() throws Exception {
        ChatResp resp = ChatResp.builder()
                .messageId("test-message-id")
                .sessionId("test-session-id")
                .role("assistant")
                .content("我听到你了。")
                .emotionIntensity(5)
                .safetyFlag(false)
                .createdAt(SAMPLE_DT)
                .build();

        String json = objectMapper.writeValueAsString(resp);

        assertThat(json).contains("\"message_id\"");
        assertThat(json).contains("\"created_at\":\"" + EXPECTED_STR + "\"");
    }

    // ── RewriteResp.createdAt ──────────────────────────────────────────────

    @Test
    void rewriteResp_createdAt_uses_correct_format() throws Exception {
        RewriteResp resp = RewriteResp.builder()
                .rewriteId("test-rewrite-id")
                .variants(List.of(
                        RewriteResp.VariantResp.builder()
                                .version("gentle")
                                .content("我想跟你说几句话。")
                                .riskLevel("low")
                                .riskReason("措辞温和，情绪稳定")
                                .sendRecommended(true)
                                .confidence(0.85)
                                .build()
                ))
                .createdAt(SAMPLE_DT)
                .build();

        String json = objectMapper.writeValueAsString(resp);

        assertThat(json).contains("\"rewrite_id\"");
        assertThat(json).contains("\"created_at\":\"" + EXPECTED_STR + "\"");
        assertThat(json).contains("\"send_recommended\"");
        assertThat(json).contains("\"risk_reason\"");
    }

    // ── IResult 包装结构 ───────────────────────────────────────────────────

    @Test
    void iResult_wrapper_has_correct_structure() throws Exception {
        AssessmentResp inner = AssessmentResp.builder()
                .assessmentId("abc")
                .score(63)
                .level(AssessmentLevel.YELLOW)
                .confidence(1.0)
                .createdAt(SAMPLE_DT)
                .build();

        String json = objectMapper.writeValueAsString(IResult.success(inner));

        assertThat(json).contains("\"code\":200");
        assertThat(json).contains("\"message\":\"success\"");
        assertThat(json).contains("\"data\"");
        assertThat(json).contains("\"created_at\":\"" + EXPECTED_STR + "\"");
    }
}
