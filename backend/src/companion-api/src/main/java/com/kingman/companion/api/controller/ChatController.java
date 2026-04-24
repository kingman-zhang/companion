package com.kingman.companion.api.controller;

import com.kingman.companion.framework.annotation.SkipCheckLoginAuth;
import com.kingman.companion.framework.common.IResult;
import com.kingman.companion.module.chat.req.ChatReq;
import com.kingman.companion.module.chat.resp.ChatResp;
import com.kingman.companion.module.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 情绪急救聊天接口
 * POST /api/v1/chat
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

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
     */
    @PostMapping("/session")
    @SkipCheckLoginAuth
    public IResult<Map<String, String>> createSession() {
        String sessionId = chatService.createSession();
        return IResult.success(Map.of("session_id", sessionId));
    }
}
