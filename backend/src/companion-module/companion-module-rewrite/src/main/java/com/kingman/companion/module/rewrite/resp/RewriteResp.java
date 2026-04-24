package com.kingman.companion.module.rewrite.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息改写响应体
 */
@Data
@Builder
public class RewriteResp {

    private String rewriteId;
    /** 固定 3 个版本（gentle / direct / brief） */
    private List<VariantResp> variants;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class VariantResp {
        private String version;
        private String content;
        private String riskLevel;
        private String riskReason;
        private Boolean sendRecommended;
        private Double confidence;
    }
}
