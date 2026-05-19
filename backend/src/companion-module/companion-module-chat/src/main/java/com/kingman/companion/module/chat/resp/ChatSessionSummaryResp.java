package com.kingman.companion.module.chat.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatSessionSummaryResp {
    private String sessionId;
    private String preview;
    private int roundCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
