package com.kingman.companion.framework.logger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import static com.kingman.companion.framework.logger.LoggerProperties.PREFIX;

/**
 * @author kingman
 */
@ConditionalOnProperty(value = PREFIX + ".enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({LoggerProperties.class})
public class LoggerAutoConfiguration {
}
