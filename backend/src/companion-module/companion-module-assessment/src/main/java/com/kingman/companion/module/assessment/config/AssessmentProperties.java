package com.kingman.companion.module.assessment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 评估模块配置（来自 {@code companion.assessment.*}）
 */
@Data
@Component
@ConfigurationProperties(prefix = "companion.assessment")
public class AssessmentProperties {

    /**
     * 评估洞察生成提示词（core_insight + llm_reason），
     * 可在 application.yml 中修改，无需重新编译。
     */
    private String systemPrompt = "";

    /**
     * 用户背景摘要生成提示词，供聊天模块 system prompt 注入使用，
     * 可在 application.yml 中修改，无需重新编译。
     */
    private String contextSummaryPrompt = "";
}
