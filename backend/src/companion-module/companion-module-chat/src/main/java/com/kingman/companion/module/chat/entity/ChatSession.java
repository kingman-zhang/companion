package com.kingman.companion.module.chat.entity;

import com.kingman.companion.framework.common.AbstractBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 聊天会话
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "chat_sessions")
public class ChatSession extends AbstractBaseEntity {

    private String userId;
    /** 关联评估 ID（可选） */
    private String assessmentId;
    /** 评估上下文摘要（注入到 system prompt，可选） */
    private String assessmentContext;
    /** 已使用轮次（免费层限制 10 轮） */
    private int roundCount;
    /** 会话是否在冷却期 */
    private boolean inCooldown;
}
