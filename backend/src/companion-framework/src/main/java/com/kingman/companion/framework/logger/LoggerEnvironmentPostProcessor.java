package com.kingman.companion.framework.logger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import static com.kingman.companion.framework.logger.LoggerProperties.PREFIX;


/**
 * @author kingman
 */
public class LoggerEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private final static String SPRING_BOOT_LOGGING_CONFIG = "logging.config";
    private final static String LOGGING_ENABLED = PREFIX + ".enabled";
    private final static String LOGGING_CONFIG = PREFIX + ".config";
    private final static String LOGGING_EAGER_LOAD_ENABLED = PREFIX + ".eagerLoad.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String loggingConfigFile = environment.getProperty(SPRING_BOOT_LOGGING_CONFIG);
        if (loggingConfigFile != null && !loggingConfigFile.isEmpty()) {
            // 已经有配置的日志配置文件，不需要修改
            return;
        }

        Boolean loggingEnabled = environment.getProperty(LOGGING_ENABLED, Boolean.class, true);
        Boolean loggingEagerLoadEnabled = environment.getProperty(LOGGING_EAGER_LOAD_ENABLED, Boolean.class, true);
        String configFile = environment.getProperty(LOGGING_CONFIG, String.class, "classpath:logback/logback-spring.xml");
        if (loggingEnabled && loggingEagerLoadEnabled) {
            // 设置日志配置
            System.setProperty(SPRING_BOOT_LOGGING_CONFIG, configFile);
        }
    }
}
