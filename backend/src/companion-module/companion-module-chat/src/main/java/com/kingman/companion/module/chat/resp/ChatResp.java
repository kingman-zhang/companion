package com.kingman.companion.module.chat.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kingman.companion.component.enums.EmotionLabel;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息响应体
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResp {

    private String messageId;
    private String sessionId;
    /** 固定 assistant */
    private String role;
    /** AI 回复，≤150 字 */
    private String content;
    private EmotionLabel emotionLabel;
    /** 情绪强度 0–10 */
    private Integer emotionIntensity;
    private Boolean safetyFlag;
    /** 微干预卡片（intensity ≥ 8 时返回） */
    private MicroIntervention microIntervention;
    private LocalDateTime createdAt;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MicroIntervention {
        /** breathe / step_away / delay_send */
        private String type;
        private String title;
        private String body;
        private String actionLabel;
        private String actionTarget;
        private String secondaryActionLabel;
        private String secondaryActionTarget;
    }
}
