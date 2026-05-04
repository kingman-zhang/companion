package com.kingman.companion.module.chat.req;

import lombok.Data;

/**
 * 创建聊天会话请求体（所有字段可选）
 */
@Data
public class CreateSessionReq {

    /**
     * 评估结果 ID（可选）。
     * 传入时，后端会将评估摘要注入到会话的 system prompt，
     * 让 AI 了解用户的关系背景；不传则直接进入聊天。
     */
    private String assessmentId;
}
