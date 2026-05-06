package com.kingman.companion.module.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信小程序认证配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "companion.wx")
public class WxAuthProperties {

    private String appId;
    private String appSecret;
    private String code2SessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
}
