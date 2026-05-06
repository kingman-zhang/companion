package com.kingman.companion.module.chat.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kingman.companion.component.enums.EmotionLabel;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageHistoryResp {
    private String messageId;
    private String role;
    private String content;
    private EmotionLabel emotionLabel;
    private Integer emotionIntensity;
    private LocalDateTime createdAt;
}
