package com.kingman.companion.module.log.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserFeedbackResp {

    private String feedbackId;
    private String type;
    private String content;
    private String contact;
    private LocalDateTime createdAt;
}
