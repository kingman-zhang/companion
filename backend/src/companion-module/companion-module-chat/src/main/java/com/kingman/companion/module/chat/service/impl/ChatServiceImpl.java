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
import com.kingman.companion.framework.exception.ClientDisconnectedException;
import com.kingman.companion.framework.security.AuthContext;
import com.kingman.companion.framework.util.DistributeID;
import com.kingman.companion.module.chat.entity.ChatMessage;
import com.kingman.companion.module.chat.entity.ChatSession;
import com.kingman.companion.module.chat.repository.ChatMessageRepository;
import com.kingman.companion.module.chat.repository.ChatSessionRepository;
import com.kingman.companion.module.chat.req.ChatReq;
import com.kingman.companion.module.chat.resp.ChatMessageHistoryResp;
import com.kingman.companion.module.chat.resp.ChatResp;
import com.kingman.companion.module.chat.resp.ChatSessionSummaryResp;
import com.kingman.companion.module.chat.config.ChatProperties;
import com.kingman.companion.module.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

    private static final String METADATA_SEPARATOR = "###METADATA###";

    /** 深度分析请求识别关键词（命中时路由到 ADVANCED 模型） */
    private static final List<String> DEEP_ANALYSIS_KEYWORDS = List.of(
            "深度分析", "帮我分析", "全面分析", "深入分析", "详细分析",
            "全面评估", "系统分析", "综合分析", "你觉得我们还有没有希望",
            "告诉我原因", "分析一下为什么"
    );
    private static final List<String> DIRECT_MESSAGE_HINTS = List.of(
            "我要发", "想发", "发给他", "发给她", "发给你", "要不要发",
            "该不该发", "怎么回", "该怎么回", "怎么回复", "帮我回",
            "帮我写", "帮我改写", "我该怎么说", "我要怎么说", "要怎么说",
            "怎么跟他说", "怎么跟她说", "怎么和他说", "怎么和她说",
            "要不要联系", "该不该联系", "要不要找他", "要不要找她",
            "发消息", "回消息", "回复他", "回复她"
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

        log.info("[Chat] 收到消息: session={}, userId={}, round={}, safety={}, msgLen={}",
                req.getSessionId(), session.getUserId(), session.getRoundCount(),
                safety.level(), req.getContent().length());

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
        String systemPrompt = buildSystemPrompt(session.getAssessmentContext());
        String llmText = llmGateway.completeWithHistory(systemPrompt, messages, context);

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

        // Set preview from first user message
        if (session.getRoundCount() == 0 && (session.getPreview() == null || session.getPreview().isBlank())) {
            String previewText = req.getContent();
            session.setPreview(previewText.substring(0, Math.min(previewText.length(), 50)));
        }

        // 更新会话轮次
        session.setRoundCount(session.getRoundCount() + 1);
        sessionRepository.save(session);

        log.info("[Chat] 回复完成: session={}, emotion={}, intensity={}, replyLen={}",
                req.getSessionId(), result.emotionLabel(), result.emotionIntensity(), result.reply().length());

        ChatResp.ChatRespBuilder builder = ChatResp.builder()
                .messageId(savedAiMsg.getId())
                .sessionId(req.getSessionId())
                .role("assistant")
                .content(result.reply())
                .emotionLabel(result.emotionLabel())
                .emotionIntensity(result.emotionIntensity())
                .safetyFlag(false)
                .createdAt(savedAiMsg.getCreateTime());

        ChatResp.MicroIntervention microIntervention = buildMicroIntervention(result.emotionLabel(), result.emotionIntensity(), req.getContent());
        if (microIntervention != null) {
            builder.microIntervention(microIntervention);
        }

        return builder.build();
    }

    @Override
    public String createSession(String assessmentContext) {
        ChatSession session = new ChatSession();
        session.setId(DistributeID.generate());
        session.setUserId(AuthContext.getCurrentUserId()); // null if anonymous
        session.setRoundCount(0);
        session.setInCooldown(false);
        session.setAssessmentContext(assessmentContext);
        return sessionRepository.save(session).getId();
    }

    // ── System Prompt 构建 ────────────────────────────────────────────────────

    private String buildSystemPrompt(String assessmentContext) {
        return buildSystemPrompt(chatProperties.getSystemPrompt(), assessmentContext);
    }

    private String buildSystemPrompt(String basePrompt, String assessmentContext) {
        if (assessmentContext == null || assessmentContext.isBlank()) {
            return basePrompt;
        }
        return assessmentContext + "\n\n" + basePrompt;
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
            String json = extractJson(llmText);

            // Model returned plain text (no JSON) — use it directly as the reply
            if (!json.startsWith("{")) {
                log.warn("Chat LLM 返回纯文本（非 JSON），降级处理: len={}，返回内容为：{}", llmText.length(),llmText);
                return new LlmResult(llmText.trim(), EmotionLabel.OTHER, 5);
            }

            JsonNode root = MAPPER.readTree(json);
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

    private ChatResp.MicroIntervention buildMicroIntervention(EmotionLabel emotion, int intensity, String userContent) {
        if (intensity < 8) {
            return null;
        }
        return switch (emotion) {
            case ANXIETY -> ChatResp.MicroIntervention.builder()
                    .type("breathe")
                    .title("先把自己稳一下")
                    .body("你现在不用急着想清楚。先慢慢呼一口气，让身体先从紧绷里退下来。")
                    .actionLabel("好，我先缓一下")
                    .build();
            case ANGER -> ChatResp.MicroIntervention.builder()
                    .type("step_away")
                    .title("先离开这个情绪一下")
                    .body("你现在的委屈和火气都很真实。先别急着顶回去，给自己留一点回旋空间。")
                    .actionLabel("好，我先停一下")
                    .build();
            default -> shouldSuggestDelaySend(userContent)
                    ? ChatResp.MicroIntervention.builder()
                        .type("delay_send")
                        .title("这句话先别急着发")
                        .body("你可以先把想说的话放在这里，我们一起把它整理得更稳一点，再决定要不要发出去。")
                        .actionLabel("帮我改写一下")
                        .actionTarget("/rewrite")
                        .secondaryActionLabel("我先收着")
                        .secondaryActionTarget("close")
                        .build()
                    : null;
        };
    }

    private boolean shouldSuggestDelaySend(String userContent) {
        if (userContent == null) return false;
        String normalized = userContent.replaceAll("\\s+", "");
        if (normalized.isBlank()) return false;

        for (String hint : DIRECT_MESSAGE_HINTS) {
            if (normalized.contains(hint)) {
                return true;
            }
        }

        return normalized.length() <= 80
                && (normalized.startsWith("你")
                || normalized.startsWith("凭什么")
                || normalized.startsWith("为什么")
                || normalized.startsWith("算了")
                || normalized.startsWith("分手")
                || normalized.startsWith("别再")
                || normalized.startsWith("以后别"));
    }

    record LlmResult(String reply, EmotionLabel emotionLabel, int emotionIntensity) {}

    record StreamResult(String reply, EmotionLabel emotionLabel, int emotionIntensity) {}

    // ── 流式发送 ──────────────────────────────────────────────────────────────

    @Override
    public SseEmitter streamMessage(ChatReq req) {
        // 安全检测（前置，同非流式）
        SafetyResult safety = safetyChecker.check(req.getContent());
        safety.throwIfBlocked();

        ChatSession session = sessionRepository.findByIdAndDeletedFalse(req.getSessionId())
                .orElseThrow(() -> new ApiException(CodeEnum.NOT_FOUND));

        log.info("[Chat-Stream] 收到消息: session={}, round={}, safety={}, msgLen={}",
                req.getSessionId(), session.getRoundCount(), safety.level(), req.getContent().length());

        if (session.getRoundCount() >= FREE_TIER_ROUND_LIMIT) {
            SseEmitter emitter = new SseEmitter(30_000L);
            sendStructuredError(emitter, CodeEnum.FREE_TIER_LIMIT_REACHED.getCode(), CodeEnum.FREE_TIER_LIMIT_REACHED.getMessage());
            emitter.complete();
            return emitter;
        }

        List<ChatMessage> historyDesc = messageRepository.findTop10BySessionIdAndDeletedFalse(
                req.getSessionId(), Sort.by(Sort.Direction.DESC, "createTime"));
        List<ChatMessage> history = new ArrayList<>(historyDesc);
        Collections.reverse(history);

        ChatMessage userMsg = new ChatMessage();
        userMsg.setId(DistributeID.generate());
        userMsg.setSessionId(req.getSessionId());
        userMsg.setRole("user");
        userMsg.setContent(req.getContent());
        userMsg.setSafetyFlag(false);
        messageRepository.save(userMsg);

        List<LlmMessage> messages = buildMessages(history, req.getContent());
        int totalInputLength = messages.stream().mapToInt(m -> m.content().length()).sum();
        RoutingContext context = buildRoutingContext(req.getContent(), totalInputLength, safety.level());

        String streamPrompt = chatProperties.getStreamSystemPrompt();
        if (streamPrompt == null || streamPrompt.isBlank()) {
            streamPrompt = chatProperties.getSystemPrompt();
        }
        String systemPrompt = buildSystemPrompt(streamPrompt, session.getAssessmentContext());

        SseEmitter emitter = new SseEmitter(90_000L);
        final String finalSystemPrompt = systemPrompt;
        final ChatSession finalSession = session;

        Thread thread = new Thread(() -> runStream(emitter, req, finalSystemPrompt, messages, context, finalSession));
        thread.setName("chat-stream-" + req.getSessionId());
        thread.setDaemon(true);
        thread.start();

        return emitter;
    }

    private void runStream(SseEmitter emitter, ChatReq req, String systemPrompt,
                           List<LlmMessage> messages, RoutingContext context, ChatSession session) {
        long streamStart = System.currentTimeMillis();
        StringBuilder accumulated = new StringBuilder();
        int[] sentUpTo = {0};
        boolean[] firstChunkLogged = {false};

        try {
            log.info("[Chat-Stream] 开始请求模型: session={}, msgLen={}", req.getSessionId(), req.getContent().length());
            llmGateway.streamWithHistory(systemPrompt, messages, context, chunk -> {
                if (!firstChunkLogged[0]) {
                    firstChunkLogged[0] = true;
                    long firstChunkElapsed = System.currentTimeMillis() - streamStart;
                    log.info("[Chat-Stream] 收到首个chunk: session={}, firstChunkElapsed={}ms, chunkLen={}",
                            req.getSessionId(), firstChunkElapsed, chunk != null ? chunk.length() : 0);
                }
                accumulated.append(chunk);
                String full = accumulated.toString();

                int sepIdx = full.indexOf(METADATA_SEPARATOR);
                // Send text up to either the separator or (length - 14) to avoid split separator
                int holdBack = METADATA_SEPARATOR.length() - 1;
                int sendUpTo = sepIdx >= 0 ? sepIdx : Math.max(sentUpTo[0], full.length() - holdBack);

                if (sendUpTo > sentUpTo[0]) {
                    String toSend = full.substring(sentUpTo[0], sendUpTo);
                    sendDelta(emitter, toSend);
                    sentUpTo[0] = sendUpTo;
                }
            });

            // Flush remaining text before separator (or all if no separator found)
            String full = accumulated.toString();
            int sepIdx = full.indexOf(METADATA_SEPARATOR);
            if (sepIdx > sentUpTo[0]) {
                sendDelta(emitter, full.substring(sentUpTo[0], sepIdx));
            } else if (sepIdx < 0 && sentUpTo[0] < full.length()) {
                sendDelta(emitter, full.substring(sentUpTo[0]));
            }

            // Parse metadata
            StreamResult result = parseStreamResult(full);

            // Save AI message
            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setId(DistributeID.generate());
            aiMsg.setSessionId(req.getSessionId());
            aiMsg.setRole("assistant");
            aiMsg.setContent(result.reply());
            aiMsg.setEmotionLabel(result.emotionLabel());
            aiMsg.setEmotionIntensity(result.emotionIntensity());
            aiMsg.setSafetyFlag(false);
            ChatMessage savedAiMsg = messageRepository.save(aiMsg);

            if (session.getRoundCount() == 0 && (session.getPreview() == null || session.getPreview().isBlank())) {
                String p = req.getContent();
                session.setPreview(p.substring(0, Math.min(p.length(), 50)));
            }
            session.setRoundCount(session.getRoundCount() + 1);
            sessionRepository.save(session);

            // Build done event
            String doneJson = String.format(
                    "{\"type\":\"done\",\"messageId\":\"%s\",\"emotionLabel\":\"%s\",\"emotionIntensity\":%d%s}",
                    savedAiMsg.getId(),
                    result.emotionLabel().name(),
                    result.emotionIntensity(),
                    buildMicroInterventionJson(result.emotionLabel(), result.emotionIntensity(), req.getContent())
            );
            emitter.send(SseEmitter.event().data(doneJson, MediaType.TEXT_PLAIN));
            emitter.complete();

            long totalElapsed = System.currentTimeMillis() - streamStart;
            log.info("[Chat-Stream] 完成: session={}, emotion={}, intensity={}, replyLen={}, totalElapsed={}ms",
                    req.getSessionId(), result.emotionLabel(), result.emotionIntensity(), result.reply().length(), totalElapsed);

        } catch (ApiException e) {
            sendError(emitter, e.getMessage() != null ? e.getMessage() : "服务暂时不可用");
            emitter.completeWithError(e);
        } catch (ClientDisconnectedException e) {
            long totalElapsed = System.currentTimeMillis() - streamStart;
            log.info("[Chat-Stream] 客户端断开: session={}, elapsed={}ms, firstChunkReceived={}",
                    req.getSessionId(), totalElapsed, firstChunkLogged[0]);
            emitter.complete();
        } catch (Exception e) {
            log.error("[Chat-Stream] 流式失败: session={}", req.getSessionId(), e);
            sendError(emitter, "服务暂时不可用，请重试");
            emitter.completeWithError(e);
        }
    }

    private void sendDelta(SseEmitter emitter, String content) {
        if (content == null || content.isEmpty()) return;
        try {
            String escaped = content
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
            emitter.send(SseEmitter.event().data("{\"type\":\"delta\",\"content\":\"" + escaped + "\"}", MediaType.TEXT_PLAIN));
        } catch (IOException e) {
            log.debug("[Chat-Stream] 发送 delta 失败（客户端可能已断开）");
            throw new ClientDisconnectedException("client_disconnected", e);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            String escaped = message.replace("\"", "\\\"");
            emitter.send(SseEmitter.event().data("{\"type\":\"error\",\"message\":\"" + escaped + "\"}", MediaType.TEXT_PLAIN));
        } catch (IOException ignored) {}
    }

    private void sendStructuredError(SseEmitter emitter, int code, String message) {
        try {
            String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
            emitter.send(SseEmitter.event().data(
                    "{\"type\":\"error\",\"code\":" + code + ",\"message\":\"" + escaped + "\"}",
                    MediaType.TEXT_PLAIN
            ));
        } catch (IOException ignored) {}
    }

    private StreamResult parseStreamResult(String accumulated) {
        int sepIdx = accumulated.indexOf(METADATA_SEPARATOR);
        String replyText = sepIdx >= 0
                ? accumulated.substring(0, sepIdx).trim()
                : accumulated.trim();

        if (replyText.isEmpty()) {
            return new StreamResult("...", EmotionLabel.OTHER, 5);
        }

        if (sepIdx < 0) {
            return new StreamResult(replyText, EmotionLabel.OTHER, 5);
        }

        String metaRaw = accumulated.substring(sepIdx + METADATA_SEPARATOR.length()).trim();
        try {
            String metaJson = extractJson(metaRaw);
            JsonNode meta = MAPPER.readTree(metaJson);
            EmotionLabel label = parseEmotionLabel(meta.path("emotion_label").asText("OTHER"));
            int intensity = Math.min(Math.max(meta.path("emotion_intensity").asInt(5), 0), 10);
            return new StreamResult(replyText, label, intensity);
        } catch (Exception e) {
            log.warn("[Chat-Stream] 元数据解析失败，使用默认情绪: {}", metaRaw);
            return new StreamResult(replyText, EmotionLabel.OTHER, 5);
        }
    }

    private String buildMicroInterventionJson(EmotionLabel emotion, int intensity, String userContent) {
        ChatResp.MicroIntervention microIntervention = buildMicroIntervention(emotion, intensity, userContent);
        if (microIntervention == null) {
            return "";
        }

        try {
            return ",\"microIntervention\":" + MAPPER.writeValueAsString(microIntervention);
        } catch (Exception e) {
            log.warn("[Chat-Stream] 微干预序列化失败: type={}", microIntervention.getType(), e);
            return "";
        }
    }

    @Override
    public List<ChatSessionSummaryResp> listSessions() {
        String userId = AuthContext.getCurrentUserId();
        if (userId == null || userId.isBlank()) return Collections.emptyList();
        return sessionRepository.findTop20ByUserIdAndDeletedFalseOrderByCreateTimeDesc(userId)
                .stream()
                .map(s -> ChatSessionSummaryResp.builder()
                        .sessionId(s.getId())
                        .preview(s.getPreview() != null && !s.getPreview().isBlank() ? s.getPreview() : "新对话")
                        .roundCount(s.getRoundCount())
                        .createdAt(s.getCreateTime())
                        .build())
                .toList();
    }

    @Override
    public List<ChatMessageHistoryResp> getSessionMessages(String sessionId) {
        ChatSession session = sessionRepository.findByIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new ApiException(CodeEnum.NOT_FOUND));
        String userId = AuthContext.getCurrentUserId();
        if (userId != null && !userId.isBlank() && session.getUserId() != null
                && !userId.equals(session.getUserId())) {
            throw new ApiException(CodeEnum.NOT_FOUND);
        }
        return messageRepository.findBySessionIdAndDeletedFalseOrderByCreateTimeAsc(sessionId)
                .stream()
                .map(m -> ChatMessageHistoryResp.builder()
                        .messageId(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .emotionLabel(m.getEmotionLabel())
                        .emotionIntensity(m.getEmotionIntensity())
                        .createdAt(m.getCreateTime())
                        .build())
                .toList();
    }
}
