package com.kingman.companion.framework.common.logger;

import lombok.Getter;

/**
 * 日志类型枚举
 *
 * @author kingman
 */
@Getter
public enum LogFlag {

    /**
     * 请求响应日志
     */
    REQUEST_RESPONSE(1, "REQUEST_RESPONSE"),
    /**
     * 过长的响应日志
     */
    TO_LONG_RESPONSE_LOG(10, "TO_LONG_RESPONSE_LOG"),
    /**
     * 其他日志
     */
    OTHER(0, "OTHER");

    private final int flag;
    private final String name;
    private final LogFlagBasicMarker marker;

    LogFlag(int flag, String name) {
        this.flag = flag;
        this.name = name;
        this.marker = new LogFlagBasicMarker(this);

    }

    public static LogFlag get(int flag) {
        LogFlag[] values = LogFlag.values();
        for (LogFlag logFlag : values) {
            if (logFlag.getFlag() == flag) {
                return logFlag;
            }
        }
        return LogFlag.OTHER;
    }

}
