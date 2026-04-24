package com.kingman.companion.module.rewrite.service.impl;

import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import com.kingman.companion.framework.util.DistributeID;
import com.kingman.companion.module.rewrite.entity.RewriteRecord;
import com.kingman.companion.module.rewrite.entity.RewriteVariant;
import com.kingman.companion.module.rewrite.repository.RewriteRepository;
import com.kingman.companion.module.rewrite.req.RewriteReq;
import com.kingman.companion.module.rewrite.resp.RewriteResp;
import com.kingman.companion.module.rewrite.service.RewriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 改写服务实现
 * MVP 阶段：LLM 调用使用模板占位，接入真实 LLM 时替换 generateVariant 方法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RewriteServiceImpl implements RewriteService {

    private static final int FREE_TIER_DAILY_LIMIT = 1;

    private final RewriteRepository rewriteRepository;

    @Override
    public RewriteResp rewrite(RewriteReq req) {
        // TODO: 接入真实 userId 后启用免费层限制
        // checkFreeTierLimit(userId);

        List<RewriteVariant> variants = List.of(
                generateVariant("gentle", req.getOriginalMessage()),
                generateVariant("direct", req.getOriginalMessage()),
                generateVariant("brief", req.getOriginalMessage())
        );

        RewriteRecord record = new RewriteRecord();
        record.setId(DistributeID.generate());
        record.setSessionId(req.getSessionId());
        record.setOriginalMessage(req.getOriginalMessage());
        record.setVariants(variants);
        RewriteRecord saved = rewriteRepository.save(record);

        log.info("改写完成: id={}", saved.getId());

        return RewriteResp.builder()
                .rewriteId(saved.getId())
                .variants(variants.stream()
                        .map(v -> RewriteResp.VariantResp.builder()
                                .version(v.getVersion())
                                .content(v.getContent())
                                .riskLevel(v.getRiskLevel())
                                .riskReason(v.getRiskReason())
                                .sendRecommended(v.getSendRecommended())
                                .confidence(v.getConfidence())
                                .build())
                        .toList())
                .createdAt(saved.getCreateTime())
                .build();
    }

    private void checkFreeTierLimit(String userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCount = rewriteRepository.countByUserIdAndCreateTimeAfterAndDeletedFalse(
                userId, todayStart);
        if (todayCount >= FREE_TIER_DAILY_LIMIT) {
            throw new ApiException(CodeEnum.FREE_TIER_LIMIT_REACHED);
        }
    }

    /**
     * 生成改写变体（MVP 使用模板，后续替换为 LLM 调用）
     */
    private RewriteVariant generateVariant(String version, String original) {
        RewriteVariant variant = new RewriteVariant();
        variant.setVersion(version);
        variant.setRiskLevel("low");
        variant.setSendRecommended(true);
        variant.setConfidence(0.8);

        switch (version) {
            case "gentle" -> {
                variant.setContent("我最近一直在想我们的事，有些话想跟你说。"
                        + "我知道现在可能不是好时机，但我还是想让你知道我的感受。");
                variant.setRiskReason("措辞温和，情绪稳定");
            }
            case "direct" -> {
                variant.setContent("我需要跟你认真谈一谈。我认为我们之间还有一些没解决的问题，"
                        + "希望能找个时间当面聊清楚。");
                variant.setRiskLevel("medium");
                variant.setRiskReason("直接表达诉求，对方可能有压力感");
            }
            case "brief" -> {
                variant.setContent("有些话想跟你说，方便的话能给我一点时间吗？");
                variant.setRiskReason("简短直接，降低对方防御感");
            }
        }
        return variant;
    }
}
