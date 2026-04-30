package com.kingman.companion.module.chat.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingman.companion.component.enums.EmotionLabel;
import com.kingman.companion.component.llm.AnthropicClient;
import com.kingman.companion.component.llm.AnthropicMessage;
import com.kingman.companion.component.safety.SafetyChecker;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import com.kingman.companion.framework.util.DistributeID;
import com.kingman.companion.module.chat.entity.ChatMessage;
import com.kingman.companion.module.chat.entity.ChatSession;
import com.kingman.companion.module.chat.repository.ChatMessageRepository;
import com.kingman.companion.module.chat.repository.ChatSessionRepository;
import com.kingman.companion.module.chat.req.ChatReq;
import com.kingman.companion.module.chat.resp.ChatResp;
import com.kingman.companion.module.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天服务实现
 *
 * <p>调用 Claude 生成情感陪伴回复，解析 JSON 响应获取回复内容、情绪标签和情绪强度。
 * 历史消息加载后反转为时间正序，拼接当前用户消息后传入 LLM。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    // ── Prompt ──────────────────────────────────────────────────────────────

    static final String SYSTEM_PROMPT = """
            你是"伴听"，一位专注于关系危机场景的 AI 情感陪伴助手。目标用户是 22–38 岁、正经历关系危机的人。
            你的定位是：帮助用户在关系危机时刻冷静下来，做出更理性的判断和表达。不做心理治疗，不给建议，只是陪伴和疏导。

            回复原则：
            - 语气温暖、简洁，≤150 字
            - 优先反映用户情绪（共情），不急于给方案
            - 不评判用户或其对方
            - 不鼓励冲动行为（如立即质问对方）

            情绪标签（emotion_label）从以下枚举中选一个最贴近的：
            ANXIETY（焦虑）、ANGER（愤怒）、SADNESS（悲伤）、FEAR（恐惧）、GUILT（内疚）、CALM（平静）、HOPE（希望）、OTHER（其他）

            情绪强度（emotion_intensity）：0–10 整数，10 最强烈。

            严格只输出以下 JSON，不要有任何前缀、解释或额外内容：
            {"reply":"...","emotion_label":"ANXIETY","emotion_intensity":5}
            """;

    private static final int FREE_TIER_ROUND_LIMIT = 10;
    /** 加载的历史消息条数上限（每条包含 user + assistant，共 10 条消息 = 5 轮） */
    private static final int HISTORY_LIMIT = 10;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AnthropicClient llmClient;
    private final SafetyChecker safetyChecker;

    // ── Public API ────────────────────────────────────────────────────────────

    @Override
    public ChatResp sendMessage(ChatReq req) {
        // 安全检测（前置，命中则 HTTP 451）
        safetyChecker.check(req.getContent()).throwIfBlocked();

        // 加载会话
        ChatSession session = sessionRepository.findByIdAndDeletedFalse(req.getSessionId())
                .orElseThrow(() -> new ApiException(CodeEnum.NOT_FOUND));

        // 免费层轮次限制
        if (session.getRoundCount() >= FREE_TIER_ROUND_LIMIT) {
            throw new ApiException(CodeEnum.FREE_TIER_LIMIT_REACHED);
        }

        // 加载历史（DESC，取最近几条；反转为时间正序再传给 LLM）
        List<ChatMessage> historyDesc = messageRepository.findTop10BySessionIdAndDeletedFalse(
                req.getSessionId(), Sort.by(Sort.Direction.DESC, "createTime"));
        List<ChatMessage> history = new ArrayList<>(historyDesc);
        Collections.reverse(history);

        // 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setId(DistributeID.generate());
        userMsg.setSessionId(req.getSessionId());
        userMsg.setRole("user");
        userMsg.setContent(req.getContent());
        userMsg.setSafetyFlag(false);
        messageRepository.save(userMsg);

        // 构建 LLM 消息列表：历史 + 当前用户消息
        List<AnthropicMessage> messages = buildMessages(history, req.getContent());
        String llmText = llmClient.completeWithHistory(SYSTEM_PROMPT, messages);

        // 解析 LLM 响应
        LlmResult result = parseLlmResult(llmText);

        // 保存 AI 消息
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setId(DistributeID.generate());
        aiMsg.setSessionId(req.getSessionId());
        aiMsg.setRole("assistant");
        aiMsg.setContent(result.reply());
        aiMsg.setEmotionLabel(result.emotionLabel());
        aiMsg.setEmotionIntensity(result.emotionIntensity());
        aiMsg.setSafetyFlag(false);
        ChatMessage savedAiMsg = messageRepository.save(aiMsg);

        // 更新会话轮次
        session.setRoundCount(session.getRoundCount() + 1);
        sessionRepository.save(session);

        ChatResp.ChatRespBuilder builder = ChatResp.builder()
                .messageId(savedAiMsg.getId())
                .sessionId(req.getSessionId())
                .role("assistant")
                .content(result.reply())
                .emotionLabel(result.emotionLabel())
                .emotionIntensity(result.emotionIntensity())
                .safetyFlag(false)
                .createdAt(savedAiMsg.getCreateTime());

        // 微干预触发规则（intensity ≥ 8）
        if (result.emotionIntensity() >= 8) {
            builder.microIntervention(buildMicroIntervention(result.emotionLabel()));
        }

        return builder.build();
    }

    @Override
    public String createSession() {
        ChatSession session = new ChatSession();
        session.setId(DistributeID.generate());
        session.setRoundCount(0);
        session.setInCooldown(false);
        ChatSession saved = sessionRepository.save(session);
        return saved.getId();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<AnthropicMessage> buildMessages(List<ChatMessage> history, String currentUserContent) {
        List<AnthropicMessage> messages = new ArrayList<>(history.size() + 1);
        for (ChatMessage msg : history) {
            messages.add(new AnthropicMessage(msg.getRole(), msg.getContent()));
        }
        messages.add(new AnthropicMessage("user", currentUserContent));
        return messages;
    }

    /**
     * 解析 LLM 返回的 JSON：{@code {"reply":"...","emotion_label":"...","emotion_intensity":5}}
     *
     * <p>容忍 Claude 偶发的前缀文字；emotion_label 无法识别时降级为 OTHER。
     */
    LlmResult parseLlmResult(String llmText) {
        if (llmText == null || llmText.isBlank()) {
            throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
        }
        try {
            JsonNode root = MAPPER.readTree(extractJson(llmText));
            if (root == null || root.isMissingNode() || root.isNull()) {
                throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
            }

            String reply = root.path("reply").asText();
            if (reply.isBlank()) {
                log.error("LLM 返回的 reply 为空：{}", llmText);
                throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
            }

            EmotionLabel emotionLabel = parseEmotionLabel(root.path("emotion_label").asText("OTHER"));
            int emotionIntensity = Math.min(Math.max(root.path("emotion_intensity").asInt(5), 0), 10);

            return new LlmResult(reply, emotionLabel, emotionIntensity);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Chat LLM 响应解析失败，原始输出：{}", llmText, e);
            throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
        }
    }

    private EmotionLabel parseEmotionLabel(String raw) {
        try {
            return EmotionLabel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EmotionLabel.OTHER;
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return text;
        }
        return text.substring(start, end + 1);
    }

    private ChatResp.MicroIntervention buildMicroIntervention(EmotionLabel emotion) {
        return switch (emotion) {
            case ANXIETY -> ChatResp.MicroIntervention.builder()
                    .type("breathe")
                    .title("先深呼吸一下")
                    .build();
            case ANGER -> ChatResp.MicroIntervention.builder()
                    .type("step_away")
                    .title("先离开这里一会儿")
                    .build();
            default -> ChatResp.MicroIntervention.builder()
                    .type("delay_send")
                    .title("先别发那条消息")
                    .actionLabel("帮我改写它")
                    .actionTarget("/rewrite")
                    .build();
        };
    }

    /** LLM 解析结果（内部使用） */
    record LlmResult(String reply, EmotionLabel emotionLabel, int emotionIntensity) {}
}
