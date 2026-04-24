package com.kingman.companion.module.assessment.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 问卷选项定义
 */
@Data
@AllArgsConstructor
public class QuestionOption {

    /** 枚举值（提交给后端的实际值） */
    private String value;

    /** 展示给用户的中文文案 */
    private String label;

    /** 选项辅助说明（可为 null） */
    private String subDesc;
}
