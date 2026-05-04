package com.kingman.companion.api.controller;

import com.kingman.companion.api.service.AssessmentContextBuilder;
import com.kingman.companion.framework.annotation.SkipCheckLoginAuth;
import com.kingman.companion.framework.common.IResult;
import com.kingman.companion.module.assessment.resp.AssessmentResp;
import com.kingman.companion.module.assessment.service.AssessmentService;
import com.kingman.companion.module.chat.req.ChatReq;
import com.kingman.companion.module.chat.req.CreateSessionReq;
import com.kingman.companion.module.chat.resp.ChatResp;
import com.kingman.companion.module.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
