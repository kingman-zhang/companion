package com.kingman.companion.module.chat.service;

import com.kingman.companion.module.chat.req.ChatReq;
import com.kingman.companion.module.chat.resp.ChatResp;

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
     */
    String createSession();
}
