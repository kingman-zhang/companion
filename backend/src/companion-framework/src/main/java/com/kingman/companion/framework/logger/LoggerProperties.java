package com.kingman.companion.framework.logger;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.Serializable;

/**
 * @author kingman
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "companion.logging")
public class LoggerProperties implements Serializable {

    public static final String PREFIX = "companion.logging";

    /**
     * 是否开启
     */
    private boolean enabled = true;

    /**
     * 配置文件，同 logging.config
     */
    private String config;

    /**
     * 控制台输出成JSON
     */
    private boolean consoleOutJson = false;

    /**
     * 输出日志到文件相关的配置
     */
    private FileConfig file;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileConfig implements Serializable {

        /**
         * 是否开启输出日志到文件
         */
        private boolean enabled = false;

        /**
         * 日志输出的文件路径,默认值: /data/wadi/server/logs/${appName}}
         */
        private String path;

        /**
         * 日志文件的保存天数
         */
        private Integer saveDay = 14;

    }

}
