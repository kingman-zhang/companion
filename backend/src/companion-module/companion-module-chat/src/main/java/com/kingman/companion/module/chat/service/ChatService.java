package com.kingman.companion.module.chat.service;

import com.kingman.companion.module.chat.req.ChatReq;
import com.kingman.companion.module.chat.resp.ChatMessageHistoryResp;
import com.kingman.companion.module.chat.resp.ChatResp;
import com.kingman.companion.module.chat.resp.ChatSessionSummaryResp;

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
}
