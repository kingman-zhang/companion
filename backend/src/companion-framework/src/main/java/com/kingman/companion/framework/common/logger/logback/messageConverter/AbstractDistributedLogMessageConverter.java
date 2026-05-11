package com.kingman.companion.framework.common.logger.logback.messageConverter;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * logback 的消息转换器
 *
 * @author kingman
 */
@Slf4j
public abstract class AbstractDistributedLogMessageConverter extends MessageConverter {

    @Override
    public final String convert(ILoggingEvent event) {
        try {
            final Object result = process(event);
            return result == null ? "" : this.handleValue(String.valueOf(result));
        } catch (Throwable throwable) {
            log.error(throwable.getMessage(), throwable);
            return super.convert(event);
        }
    }

    protected abstract Object process(ILoggingEvent event);

    protected String handleValue(String value) {
        return StringUtils.isNotBlank(value) ? value : "";
    }

    protected Integer handleValue(Integer value) {
        return value != null ? value : 0;
    }
}
