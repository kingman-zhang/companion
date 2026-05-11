package com.kingman.companion.framework.common.logger.logback.messageConverter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.kingman.companion.framework.context.DistributedContext;

/**
 * logback 的消息转换器
 *
 * @author kingman
 */
public class DistributedLogTraceIdMessageConverter extends AbstractDistributedLogMessageConverter {
    @Override
    protected Object process(ILoggingEvent event) {
        return DistributedContext.getContext().getTraceId();
    }
}
