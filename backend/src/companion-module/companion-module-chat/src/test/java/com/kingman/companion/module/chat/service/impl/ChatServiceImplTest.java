package com.kingman.companion.module.chat.service.impl;

import com.kingman.companion.component.enums.EmotionLabel;
import com.kingman.companion.component.llm.LlmGateway;
import com.kingman.companion.component.llm.LlmMessage;
import com.kingman.companion.component.llm.RoutingContext;
import com.kingman.companion.module.chat.config.ChatProperties;
import com.kingman.companion.component.safety.SafetyChecker;
import com.kingman.companion.component.safety.SafetyResult;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import com.kingman.companion.framework.exception.SafetyBlockedException;
import com.kingman.companion.module.chat.entity.ChatMessage;
import com.kingman.companion.module.chat.entity.ChatSession;
import com.kingman.companion.module.chat.repository.ChatMessageRepository;
import com.kingman.companion.module.chat.repository.ChatSessionRepository;
import com.kingman.companion.module.chat.req.ChatReq;
import com.kingman.companion.module.chat.resp.ChatResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P1 验收测试：ChatServiceImpl LLM 接入
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private LlmGateway llmGateway;

    @Mock
    private SafetyChecker safetyChecker;

    @Mock
    private ChatProperties chatProperties;

    private ChatServiceImpl service;

    private static final String SESSION_ID = "session-001";

    private static final String VALID_LLM_RESPONSE =
            "{\"reply\":\"我听到你了，你现在感觉很焦虑，这是完全可以理解的。\",\"emotion_label\":\"ANXIETY\",\"emotion_intensity\":6}";

    @BeforeEach
    void setUp() {
        lenient().when(chatProperties.getSystemPrompt()).thenReturn("test-system-prompt");
        service = new ChatServiceImpl(sessionRepository, messageRepository, llmGateway, safetyChecker, chatProperties);

        // 默认所有内容安全（lenient：部分测试直接调用 parseLlmResult，不经过 check）
        lenient().when(safetyChecker.check(anyString())).thenReturn(SafetyResult.pass());
    }

    // ── 正常流程 ──────────────────────────────────────────────────────────────

    @Test
    void sendMessage_returns_correct_reply_and_emotion() {
        stubSession(3);
        stubNoHistory();
        stubLlm(VALID_LLM_RESPONSE);
        stubMessageSave();

        ChatResp resp = service.sendMessage(buildReq("我好担心他不回消息"));

        assertThat(resp.getContent()).isEqualTo("我听到你了，你现在感觉很焦虑，这是完全可以理解的。");
        assertThat(resp.getEmotionLabel()).isEqualTo(EmotionLabel.ANXIETY);
        assertThat(resp.getEmotionIntensity()).isEqualTo(6);
        assertThat(resp.getRole()).isEqualTo("assistant");
        assertThat(resp.getSafetyFlag()).isFalse();
    }

    @Test
    void sendMessage_increments_round_count() {
        stubSession(2);
        stubNoHistory();
        stubLlm(VALID_LLM_RESPONSE);
        stubMessageSave();

        service.sendMessage(buildReq("测试消息"));

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getRoundCount()).isEqualTo(3);
    }

    @Test
    void sendMessage_saves_both_user_and_ai_messages() {
        stubSession(0);
        stubNoHistory();
        stubLlm(VALID_LLM_RESPONSE);
        stubMessageSave();

        service.sendMessage(buildReq("用户输入内容"));

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, times(2)).save(captor.capture());

        List<ChatMessage> saved = captor.getAllValues();
        assertThat(saved.get(0).getRole()).isEqualTo("user");
        assertThat(saved.get(0).getContent()).isEqualTo("用户输入内容");
        assertThat(saved.get(1).getRole()).isEqualTo("assistant");
    }

    // ── 历史消息传递 ──────────────────────────────────────────────────────────

    @Test
    void sendMessage_passes_history_to_llm_in_chronological_order() {
        stubSession(1);
        // 模拟 repository 返回 DESC 排序的历史（最新在前）
        ChatMessage oldMsg = buildChatMessage("user", "第一条消息");
        ChatMessage newMsg = buildChatMessage("assistant", "好的，我在听");
        when(messageRepository.findTop10BySessionIdAndDeletedFalse(eq(SESSION_ID), any(Sort.class)))
                .thenReturn(List.of(newMsg, oldMsg)); // DESC：新 → 旧
        stubLlm(VALID_LLM_RESPONSE);
        stubMessageSave();

        service.sendMessage(buildReq("当前用户消息"));

        ArgumentCaptor<List<LlmMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmGateway).completeWithHistory(anyString(), messagesCaptor.capture(), any(RoutingContext.class));

        List<LlmMessage> msgs = messagesCaptor.getValue();
        // 历史反转后应为 旧 → 新 → 当前用户消息
        assertThat(msgs).hasSize(3);
        assertThat(msgs.get(0).content()).isEqualTo("第一条消息");
        assertThat(msgs.get(1).content()).isEqualTo("好的，我在听");
        assertThat(msgs.get(2).content()).isEqualTo("当前用户消息");
    }

    // ── 微干预触发 ────────────────────────────────────────────────────────────

    @Test
    void sendMessage_triggers_micro_intervention_when_intensity_gte_8() {
        stubSession(0);
        stubNoHistory();
        String highIntensityResponse =
                "{\"reply\":\"我听到你了。\",\"emotion_label\":\"ANGER\",\"emotion_intensity\":9}";
        stubLlm(highIntensityResponse);
        stubMessageSave();

        ChatResp resp = service.sendMessage(buildReq("你根本不爱我！！！"));

        assertThat(resp.getMicroIntervention()).isNotNull();
        assertThat(resp.getMicroIntervention().getType()).isEqualTo("step_away");
    }

    @Test
    void sendMessage_no_micro_intervention_when_intensity_lt_8() {
        stubSession(0);
        stubNoHistory();
        stubLlm(VALID_LLM_RESPONSE); // intensity = 6
        stubMessageSave();

        ChatResp resp = service.sendMessage(buildReq("有点担心"));

        assertThat(resp.getMicroIntervention()).isNull();
    }

    // ── 安全检测 ──────────────────────────────────────────────────────────────

    @Test
    void sendMessage_throws_SafetyBlockedException_when_content_is_unsafe() {
        when(safetyChecker.check(anyString())).thenReturn(SafetyResult.block("self_harm"));

        assertThatThrownBy(() -> service.sendMessage(buildReq("危险内容")))
                .isInstanceOf(SafetyBlockedException.class)
                .extracting(e -> ((SafetyBlockedException) e).getTriggerType())
                .isEqualTo("self_harm");

        // 拦截后不查 session，不写消息，不调 LLM
        verify(sessionRepository, never()).findByIdAndDeletedFalse(any());
        verify(messageRepository, never()).save(any());
        verify(llmGateway, never()).completeWithHistory(any(), any(), any());
    }

    @Test
    void sendMessage_checks_safety_before_session_lookup() {
        when(safetyChecker.check(anyString())).thenReturn(SafetyResult.block("violence_threat"));

        assertThatThrownBy(() -> service.sendMessage(buildReq("威胁内容")))
                .isInstanceOf(SafetyBlockedException.class);

        verifyNoInteractions(sessionRepository);
    }

    // ── 免费层限制 ────────────────────────────────────────────────────────────

    @Test
    void sendMessage_throws_when_free_tier_limit_reached() {
        stubSession(10); // 已达上限

        assertThatThrownBy(() -> service.sendMessage(buildReq("测试")))
                .isInstanceOf(ApiException.class);

        verify(llmGateway, never()).completeWithHistory(anyString(), anyList(), any());
        verify(messageRepository, never()).save(any());
    }

    // ── LLM 调用失败透传 ──────────────────────────────────────────────────────

    @Test
    void sendMessage_propagates_exception_when_llm_fails() {
        stubSession(0);
        stubNoHistory();
        when(llmGateway.completeWithHistory(anyString(), anyList(), any(RoutingContext.class)))
                .thenThrow(new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.sendMessage(buildReq("测试消息")))
                .isInstanceOf(ApiException.class);

        // AI 消息不写入
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, times(1)).save(captor.capture()); // 只有 user 消息写入
        assertThat(captor.getValue().getRole()).isEqualTo("user");
    }

    // ── parseLlmResult 单独覆盖 ───────────────────────────────────────────────

    @Test
    void parseLlmResult_handles_unknown_emotion_label_as_other() {
        String json = "{\"reply\":\"回复内容\",\"emotion_label\":\"UNKNOWN_LABEL\",\"emotion_intensity\":5}";
        ChatServiceImpl.LlmResult result = service.parseLlmResult(json);

        assertThat(result.emotionLabel()).isEqualTo(EmotionLabel.OTHER);
        assertThat(result.reply()).isEqualTo("回复内容");
    }

    @Test
    void parseLlmResult_clamps_intensity_to_valid_range() {
        String json = "{\"reply\":\"回复\",\"emotion_label\":\"CALM\",\"emotion_intensity\":15}";
        ChatServiceImpl.LlmResult result = service.parseLlmResult(json);

        assertThat(result.emotionIntensity()).isEqualTo(10);
    }

    @Test
    void parseLlmResult_tolerates_prefix_text() {
        String jsonWithPrefix = "当然，这是我的回复：\n{\"reply\":\"温柔的回复\",\"emotion_label\":\"SADNESS\",\"emotion_intensity\":7}";
        ChatServiceImpl.LlmResult result = service.parseLlmResult(jsonWithPrefix);

        assertThat(result.reply()).isEqualTo("温柔的回复");
        assertThat(result.emotionLabel()).isEqualTo(EmotionLabel.SADNESS);
    }

    @Test
    void parseLlmResult_throws_on_empty_input() {
        assertThatThrownBy(() -> service.parseLlmResult(""))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void parseLlmResult_throws_on_malformed_json() {
        // 有大括号结构但内容不合法 → Jackson 解析失败 → ApiException
        assertThatThrownBy(() -> service.parseLlmResult("{这不是合法JSON}"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void parseLlmResult_accepts_plain_text_reply_when_no_json_structure() {
        // LLM 偶发返回纯文本（无 JSON），降级为直接使用文本作为回复
        ChatServiceImpl.LlmResult result = service.parseLlmResult("我理解你的感受，先深呼吸一下。");
        assertThat(result.reply()).isEqualTo("我理解你的感受，先深呼吸一下。");
        assertThat(result.emotionLabel()).isEqualTo(EmotionLabel.OTHER);
        assertThat(result.emotionIntensity()).isEqualTo(5);
    }

    @Test
    void parseLlmResult_throws_when_reply_is_blank() {
        String json = "{\"reply\":\"\",\"emotion_label\":\"OTHER\",\"emotion_intensity\":5}";
        assertThatThrownBy(() -> service.parseLlmResult(json))
                .isInstanceOf(ApiException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubSession(int roundCount) {
        ChatSession session = new ChatSession();
        session.setId(SESSION_ID);
        session.setRoundCount(roundCount);
        session.setInCooldown(false);
        when(sessionRepository.findByIdAndDeletedFalse(SESSION_ID)).thenReturn(Optional.of(session));
    }

    private void stubNoHistory() {
        when(messageRepository.findTop10BySessionIdAndDeletedFalse(eq(SESSION_ID), any(Sort.class)))
                .thenReturn(List.of());
    }

    private void stubLlm(String response) {
        when(llmGateway.completeWithHistory(anyString(), anyList(), any(RoutingContext.class))).thenReturn(response);
    }

    private void stubMessageSave() {
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            msg.setCreateTime(LocalDateTime.of(2026, 4, 30, 10, 0, 0));
            return msg;
        });
    }

    private ChatReq buildReq(String content) {
        ChatReq req = new ChatReq();
        req.setSessionId(SESSION_ID);
        req.setContent(content);
        return req;
    }

    private ChatMessage buildChatMessage(String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setRole(role);
        msg.setContent(content);
        return msg;
    }
}
