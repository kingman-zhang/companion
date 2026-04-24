package com.kingman.companion.module.chat.entity;

import com.kingman.companion.component.enums.EmotionLabel;
import com.kingman.companion.framework.common.AbstractBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 聊天消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "chat_messages")
public class ChatMessage extends AbstractBaseEntity {

    private String sessionId;
    /** user / assistant */
    private String role;
    /** 消息内容，≤2000字 */
    private String content;
    private EmotionLabel emotionLabel;
    /** 情绪强度 0–10 */
    private Integer emotionIntensity;
    private Boolean safetyFlag;
}
