package com.kingman.companion.module.rewrite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 消息改写模块配置（来自 {@code companion.rewrite.*}）
 */
@Data
@Component
@ConfigurationProperties(prefix = "companion.rewrite")
public class RewriteProperties {

    /**
     * 改写生成提示词（gentle / direct / brief 三版本），
     * 可在 application.yml 中修改，无需重新编译。
     */
    private String systemPrompt = "";
}
