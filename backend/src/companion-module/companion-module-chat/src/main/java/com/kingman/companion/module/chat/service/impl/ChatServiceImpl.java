package com.kingman.companion.module.chat.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingman.companion.component.enums.EmotionLabel;
import com.kingman.companion.component.llm.LlmGateway;
import com.kingman.companion.component.llm.LlmMessage;
import com.kingman.companion.component.llm.ModelTier;
import com.kingman.companion.component.llm.RoutingContext;
import com.kingman.companion.component.safety.SafetyChecker;
import com.kingman.companion.component.safety.SafetyLevel;
import com.kingman.companion.component.safety.SafetyResult;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import com.kingman.companion.framework.util.DistributeID;
import com.kingman.companion.module.chat.entity.ChatMessage;
import com.kingman.companion.module.chat.entity.ChatSession;
import com.kingman.companion.module.chat.repository.ChatMessageRepository;
import com.kingman.companion.module.chat.repository.ChatSessionRepository;
import com.kingman.companion.module.chat.req.ChatReq;
import com.kingman.companion.module.chat.resp.ChatResp;
import com.kingman.companion.module.chat.config.ChatProperties;
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
 * <p>路由策略：
 * <ul>
 *   <li>安全检测 CONCERNING → SAFETY 模型（自动，由 Gateway 处理）</li>
 *   <li>用户请求深度分析 → ADVANCED 模型</li>
 *   <li>输入总长超过阈值 → LONG_CONTEXT 模型（由 Router 自动上调）</li>
 *   <li>普通聊天 → LITE 模型</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** 深度分析请求识别关键词（命中时路由到 ADVANCED 模型） */
    private static final List<String> DEEP_ANALYSIS_KEYWORDS = List.of(
            "深度分析", "帮我分析", "全面分析", "深入分析", "详细分析",
            "全面评估", "系统分析", "综合分析", "你觉得我们还有没有希望",
            "告诉我原因", "分析一下为什么"
    );

    private static final int FREE_TIER_ROUND_LIMIT = 10;
    private static final int HISTORY_LIMIT = 10;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final LlmGateway llmGateway;
    private final SafetyChecker safetyChecker;
    private final ChatProperties chatProperties;

    // ── Public API ────────────────────────────────────────────────────────────

    @Override
    public ChatResp sendMessage(ChatReq req) {
        // 安全检测（前置）
        SafetyResult safety = safetyChecker.check(req.getContent());
        safety.throwIfBlocked(); // BLOCKED → HTTP 451

        // 加载会话
        ChatSession session = sessionRepository.findByIdAndDeletedFalse(req.getSessionId())
                .orElseThrow(() -> new ApiException(CodeEnum.NOT_FOUND));

        // 免费层轮次限制
        if (session.getRoundCount() >= FREE_TIER_ROUND_LIMIT) {
            throw new ApiException(CodeEnum.FREE_TIER_LIMIT_REACHED);
        }

        // 加载历史（DESC → 反转为时间正序）
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

        // 构建 LLM 消息列表
        List<LlmMessage> messages = buildMessages(history, req.getContent());

        // 构建路由上下文
        int totalInputLength = messages.stream().mapToInt(m -> m.content().length()).sum();
        RoutingContext context = buildRoutingContext(req.getContent(), totalInputLength, safety.level());

        // 调用 LLM Gateway（路由 + fallback 由 Gateway 处理）
        String llmText = llmGateway.completeWithHistory(chatProperties.getSystemPrompt(), messages, context);

        // 解析响应
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
        return sessionRepository.save(session).getId();
    }

    // ── 路由上下文构建 ────────────────────────────────────────────────────────

    private RoutingContext buildRoutingContext(String content, int totalInputLength, SafetyLevel safetyLevel) {
        // 深度分析请求 → ADVANCED
        if (isDeepAnalysisRequest(content)) {
            return RoutingContext.deepAnalysis(totalInputLength, safetyLevel);
        }
        // 普通聊天 → LITE（Router 会根据 inputLength / safetyLevel 自动上调）
        return RoutingContext.chat(totalInputLength, safetyLevel);
    }

    private boolean isDeepAnalysisRequest(String content) {
        for (String kw : DEEP_ANALYSIS_KEYWORDS) {
            if (content.contains(kw)) return true;
        }
        return false;
    }

    // ── LLM 响应解析 ──────────────────────────────────────────────────────────

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
                log.error("LLM reply 为空：{}", llmText);
                throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
            }

            EmotionLabel emotionLabel = parseEmotionLabel(root.path("emotion_label").asText("OTHER"));
            int emotionIntensity = Math.min(Math.max(root.path("emotion_intensity").asInt(5), 0), 10);

            return new LlmResult(reply, emotionLabel, emotionIntensity);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Chat LLM 响应解析失败：{}", llmText, e);
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
        if (start < 0 || end <= start) return text;
        return text.substring(start, end + 1);
    }

    private List<LlmMessage> buildMessages(List<ChatMessage> history, String currentContent) {
        List<LlmMessage> messages = new ArrayList<>(history.size() + 1);
        for (ChatMessage msg : history) {
            messages.add(new LlmMessage(msg.getRole(), msg.getContent()));
        }
        messages.add(LlmMessage.user(currentContent));
        return messages;
    }

    private ChatResp.MicroIntervention buildMicroIntervention(EmotionLabel emotion) {
        return switch (emotion) {
            case ANXIETY -> ChatResp.MicroIntervention.builder()
                    .type("breathe").title("先深呼吸一下").build();
            case ANGER -> ChatResp.MicroIntervention.builder()
                    .type("step_away").title("先离开这里一会儿").build();
            default -> ChatResp.MicroIntervention.builder()
                    .type("delay_send").title("先别发那条消息")
                    .actionLabel("帮我改写它").actionTarget("/rewrite").build();
        };
    }

    record LlmResult(String reply, EmotionLabel emotionLabel, int emotionIntensity) {}
}
