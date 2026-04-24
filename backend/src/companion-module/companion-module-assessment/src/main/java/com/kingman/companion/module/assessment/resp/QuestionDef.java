package com.kingman.companion.module.assessment.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 问卷题目定义
 */
@Data
@AllArgsConstructor
public class QuestionDef {

    /** 题目编号，如 Q1 */
    private String id;

    /** 对应提交字段名（snake_case，与请求体一致） */
    private String field;

    /** 所属分类（面包屑展示） */
    private String category;

    /** 题目文本（大标题） */
    private String text;

    /** 题目辅助说明（副标题，可为 null） */
    private String desc;

    /** 控件类型：SINGLE（单选）/ MULTI（多选） */
    private String type;

    /** 选项列表，顺序即展示顺序 */
    private List<QuestionOption> options;
}
