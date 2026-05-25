package com.kingman.companion.module.chat.service;

import com.kingman.companion.module.chat.req.ChatReq;
import com.kingman.companion.module.chat.resp.ChatMessageHistoryResp;
import com.kingman.companion.module.chat.resp.ChatResp;
import com.kingman.companion.module.chat.resp.ChatSessionSummaryResp;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 聊天服务接口
 */
public interface ChatService {

    /**
     * 发送消息并获取 AI 回复
     */
    ChatResp sendMessage(ChatReq req);

    /**
     * 创建新会话，返回 session_id
     *
     * @param assessmentContext 评估上下文摘要（可为 null，表示无评估直接进入聊天）
     */
    String createSession(String assessmentContext);

    /**
     * 获取当前用户的会话列表（最多 20 条）
     */
    List<ChatSessionSummaryResp> listSessions();

    /**
     * 获取指定会话的消息历史
     */
    List<ChatMessageHistoryResp> getSessionMessages(String sessionId);

    /**
     * 流式发送消息，返回 SseEmitter。
     * AI 回复以 SSE 事件形式推送：
     * <ul>
     *   <li>{@code {"type":"delta","content":"文字chunk"}}</li>
     *   <li>{@code {"type":"done","messageId":"...","reply":"...","emotionLabel":"...","emotionIntensity":N}}</li>
     *   <li>{@code {"type":"error","message":"..."}}</li>
     * </ul>
     */
    SseEmitter streamMessage(ChatReq req);
}
