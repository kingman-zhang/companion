package com.kingman.companion.api.controller;

import com.kingman.companion.api.service.AssessmentContextBuilder;
import com.kingman.companion.framework.annotation.SkipCheckLoginAuth;
import com.kingman.companion.framework.common.IResult;
import com.kingman.companion.module.assessment.resp.AssessmentResp;
import com.kingman.companion.module.assessment.service.AssessmentService;
import com.kingman.companion.module.chat.req.ChatReq;
import com.kingman.companion.module.chat.req.CreateSessionReq;
import com.kingman.companion.module.chat.resp.ChatMessageHistoryResp;
import com.kingman.companion.module.chat.resp.ChatResp;
import com.kingman.companion.module.chat.resp.ChatSessionSummaryResp;
import com.kingman.companion.module.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 情绪急救聊天接口
 * POST /api/v1/chat
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final AssessmentService assessmentService;
    private final AssessmentContextBuilder contextBuilder;

    /**
     * 发送消息
     */
    @PostMapping
    @SkipCheckLoginAuth
    public IResult<ChatResp> sendMessage(@Valid @RequestBody ChatReq req) {
        return IResult.success(chatService.sendMessage(req));
    }

    /**
     * 流式发送消息（SSE）
     *
     * <p>返回 text/event-stream，每条事件的 data 为 JSON：
     * <ul>
     *   <li>{@code {"type":"delta","content":"..."}}</li>
     *   <li>{@code {"type":"done","messageId":"...","emotionLabel":"...","emotionIntensity":N}}</li>
     *   <li>{@code {"type":"error","message":"..."}}</li>
     * </ul>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SkipCheckLoginAuth
    public SseEmitter streamMessage(@Valid @RequestBody ChatReq req) {
        return chatService.streamMessage(req);
    }

    /**
     * 创建新会话
     *
     * <p>请求体可省略，或传入 {@code assessmentId} 将评估背景摘要注入会话 system prompt。
     * 评估加载或摘要生成失败时静默降级，不阻断聊天创建。
     */
    @PostMapping("/session")
    @SkipCheckLoginAuth
    public IResult<Map<String, String>> createSession(
            @RequestBody(required = false) CreateSessionReq req) {

        String assessmentContext = null;

        if (req != null && req.getAssessmentId() != null && !req.getAssessmentId().isBlank()) {
            try {
                AssessmentResp assessment = assessmentService.findById(req.getAssessmentId());
                assessmentContext = contextBuilder.build(assessment);
            } catch (Exception e) {
                log.warn("评估背景注入失败 assessmentId={}，降级为无背景聊天", req.getAssessmentId(), e);
            }
        }

        String sessionId = chatService.createSession(assessmentContext);
        return IResult.success(Map.of("session_id", sessionId));
    }

    /**
     * 获取当前用户的会话列表（需登录，最多 20 条）
     */
    @GetMapping("/sessions")
    @SkipCheckLoginAuth
    public IResult<List<ChatSessionSummaryResp>> listSessions() {
        return IResult.success(chatService.listSessions());
    }

    /**
     * 获取指定会话的消息历史
     */
    @GetMapping("/sessions/{sessionId}/messages")
    @SkipCheckLoginAuth
    public IResult<List<ChatMessageHistoryResp>> getSessionMessages(@PathVariable String sessionId) {
        return IResult.success(chatService.getSessionMessages(sessionId));
    }
}
