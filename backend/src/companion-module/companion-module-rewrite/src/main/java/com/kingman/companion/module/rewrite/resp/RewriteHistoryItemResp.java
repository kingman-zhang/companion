package com.kingman.companion.module.rewrite.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RewriteHistoryItemResp {
    private String rewriteId;
    private String originalMessage;
    private String gentleContent;
    private LocalDateTime createdAt;
    private List<VariantResp> variants;

    @Data
    @Builder
    public static class VariantResp {
        private String version;
        private String content;
        private String riskLevel;
        private Boolean sendRecommended;
        private Double confidence;
    }
}
