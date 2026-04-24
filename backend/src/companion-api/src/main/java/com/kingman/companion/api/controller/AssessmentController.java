package com.kingman.companion.api.controller;

import com.kingman.companion.framework.annotation.SkipCheckLoginAuth;
import com.kingman.companion.framework.common.IResult;
import com.kingman.companion.module.assessment.req.AssessmentReq;
import com.kingman.companion.module.assessment.resp.AssessmentResp;
import com.kingman.companion.module.assessment.resp.QuestionDef;
import com.kingman.companion.module.assessment.service.AssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 关系评估接口
 * POST /api/v1/assessment
 */
@RestController
@RequestMapping("/api/v1/assessment")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    /**
     * 获取问卷题目定义（7题，含选项中文文案）
     */
    @GetMapping("/questionnaire")
    @SkipCheckLoginAuth
    public IResult<List<QuestionDef>> getQuestionnaire() {
        return IResult.success(assessmentService.getQuestionnaire());
    }

    /**
     * 提交评估问卷
     */
    @PostMapping
    @SkipCheckLoginAuth
    public IResult<AssessmentResp> submit(@Valid @RequestBody AssessmentReq req) {
        return IResult.success(assessmentService.submit(req));
    }

    /**
     * 查询评估结果
     */
    @GetMapping("/{assessmentId}")
    @SkipCheckLoginAuth
    public IResult<AssessmentResp> findById(@PathVariable String assessmentId) {
        return IResult.success(assessmentService.findById(assessmentId));
    }
}
