package com.kingman.companion.api.controller;

import com.kingman.companion.api.service.AssessmentContextBuilder;
import com.kingman.companion.framework.annotation.SkipCheckLoginAuth;
import com.kingman.companion.framework.common.IResult;
import com.kingman.companion.module.assessment.resp.AssessmentResp;
import com.kingman.companion.module.assessment.service.AssessmentService;
import com.kingman.companion.module.rewrite.req.RewriteReq;
import com.kingman.companion.module.rewrite.resp.RewriteResp;
import com.kingman.companion.module.rewrite.service.RewriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息改写接口
 * POST /api/v1/rewrite
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rewrite")
@RequiredArgsConstructor
public class RewriteController {

    private final RewriteService rewriteService;
    private final AssessmentService assessmentService;
    private final AssessmentContextBuilder contextBuilder;

    /**
     * 提交改写请求
     *
     * <p>传入 {@code assessmentId} 时将评估背景摘要注入 system prompt；
     * 未传或加载失败时静默降级，不阻断改写流程。
     */
    @PostMapping
    @SkipCheckLoginAuth
    public IResult<RewriteResp> rewrite(@Valid @RequestBody RewriteReq req) {
        String assessmentContext = null;
        if (req.getAssessmentId() != null && !req.getAssessmentId().isBlank()) {
            try {
                AssessmentResp assessment = assessmentService.findById(req.getAssessmentId());
                assessmentContext = contextBuilder.build(assessment);
            } catch (Exception e) {
                log.warn("改写评估背景注入失败 assessmentId={}，降级为无背景改写", req.getAssessmentId(), e);
            }
        }
        return IResult.success(rewriteService.rewrite(req, assessmentContext));
    }
}
