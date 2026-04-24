package com.kingman.companion.module.assessment.service;

import com.kingman.companion.module.assessment.req.AssessmentReq;
import com.kingman.companion.module.assessment.resp.AssessmentResp;
import com.kingman.companion.module.assessment.resp.QuestionDef;

import java.util.List;

/**
 * 评估服务接口
 */
public interface AssessmentService {

    /**
     * 获取问卷题目定义（7题，含选项中文文案）
     */
    List<QuestionDef> getQuestionnaire();

    /**
     * 提交问卷并计算评估结果
     */
    AssessmentResp submit(AssessmentReq req);

    /**
     * 根据 ID 查询评估结果
     */
    AssessmentResp findById(String assessmentId);
}
