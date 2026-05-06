package com.kingman.companion.module.rewrite.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RewriteHistoryItemResp {
    private String rewriteId;
    private String originalMessage;
    /** gentle 版本内容，用于列表预览 */
    private String gentleContent;
    private LocalDateTime createdAt;
}
