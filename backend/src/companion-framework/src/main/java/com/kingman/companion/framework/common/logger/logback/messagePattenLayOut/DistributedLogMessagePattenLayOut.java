package com.kingman.companion.framework.common.logger.logback.messagePattenLayOut;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.LayoutBase;
import com.kingman.companion.framework.common.logger.DistributedLogEvent;
import com.kingman.companion.framework.common.logger.LogFlag;
import com.kingman.companion.framework.common.logger.LogFlagBasicMarker;
import com.kingman.companion.framework.context.DistributedContext;
import com.kingman.companion.framework.util.JsonUtils;
import com.kingman.companion.framework.util.SystemUtils;
import com.kingman.companion.framework.web.WebContext;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.MDC;
import org.slf4j.Marker;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 * 自定义的消息打印器
 *
 * @author kingman
 */
public class DistributedLogMessagePattenLayOut extends LayoutBase<ILoggingEvent> {

    private static final TimeZone LOG_TIME_ZONE = TimeZone.getTimeZone("Asia/Shanghai");

    /**
     * 单个日志打印的字符长度不超过 100*1024 个字符
     */
    protected static final int MAX_PRINT_LOG_LENGTH = 100 * 1024;

    @Override
    public String doLayout(ILoggingEvent event) {

        // 是否完整输出所有的上下文
        boolean fullOut = isFullOut();

        DistributedLogEvent.DistributedLogEventBuilder builder = DistributedLogEvent.builder();

        Level level = event.getLevel();

        builder.level(level.levelStr);
        builder.thread(event.getThreadName());

        Map<String, String> propertyMap = event.getLoggerContextVO().getPropertyMap();
        // 先获取配置
        String sysName = propertyMap.get("appName");
        if (StringUtils.isBlank(sysName)) {
            // 再获取环境环境变量
            sysName = SystemUtils.getAppName();
            // 兜底
            if (StringUtils.isBlank(sysName)) {
                sysName = "N/A";
            }
        }

        builder.sysName(sysName);

        builder.ip(SystemUtils.getLocalIp());

        builder.createTimestamp(event.getTimeStamp());
        builder.createTime(DateFormatUtils.format(new Date(event.getTimeStamp()), "yyyy-MM-dd HH:mm:ss.SSS", LOG_TIME_ZONE));

        // 解析当前日志的 flag
        LogFlag logFlag = getLogFlag(event);
        builder.flag(logFlag.getFlag());

        StackTraceElement[] stack = event.getCallerData();
        if (stack.length > 0) {
            StackTraceElement stackTraceElement = stack[0];
            builder.className(stackTraceElement.getClassName());
            builder.methodName(stackTraceElement.getMethodName());
        }
        DistributedContext context = DistributedContext.getContext();
        WebContext webContext = WebContext.getContext();

        builder.traceId(context.getTraceId());
        builder.api(context.getApi());
        builder.routeApi(context.getRouteApi());
        builder.userId(context.getUserId());
        builder.clientIp(context.getClientIp());
        builder.deviceId(context.getDeviceId());
        builder.packageNo(context.getPackageNo());

        if (fullOut) {
            builder.clientIp(context.getClientIp());
            builder.deviceId(context.getDeviceId());
            builder.fingerprint(context.getFingerprint());
            builder.appVersion(context.getAppVersion());
            builder.clientOs(context.getClientOs());
            builder.language(context.getLanguage());
            builder.systemLanguage(context.getSystemLanguage());
            builder.location(context.getLocation());
            builder.countryIsoCode(context.getCountryIsoCode());
            builder.clientIpCountryIsoCode(context.getClientIpCountryIsoCode());
            builder.simCountryIsoCode(context.getSimCountryIsoCode());
            builder.storeCode(context.getStoreCode());
        }

        String msg = event.getFormattedMessage();

        if (level.equals(Level.ERROR) || level.equals(Level.WARN)) {
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            if (throwableProxy != null) {
                builder.errName(throwableProxy.getClassName());
                ThrowableProxy throwable = (ThrowableProxy) throwableProxy;
                try (
                        StringWriter writer = new StringWriter();
                        PrintWriter printWriter = new PrintWriter(writer, true)
                ) {
                    ExceptionUtils.printRootCauseStackTrace(throwable.getThrowable(), printWriter);
                    msg += "\n" + writer;
                } catch (Throwable e) {
                    // 忽略
                }
            }
        }

        // 获取通过 MDC 传递过来的值
        if (StringUtils.isNotBlank(MDC.get("costTime"))) {
            Long costTime = Long.valueOf(MDC.get("costTime"));
            builder.costTime(costTime);
        }

        if (StringUtils.isNotBlank(MDC.get("className"))) {
            builder.className(MDC.get("className"));
        }

        if (StringUtils.isNotBlank(MDC.get("methodName"))) {
            builder.methodName(MDC.get("methodName"));
        }

        // 特殊处理请求响应日志
        if (LogFlag.REQUEST_RESPONSE.equals(logFlag)) {

            // 在MDC中获取传递过来的body信息
            String requestBody = MDC.get("requestBody");
            String responseBody = MDC.get("responseBody");
            Long bodySize = webContext.getBodySize();

            HttpServletRequest servletRequest = webContext.getServletRequest();
            Map<String, String> requestHeaders = new LinkedHashMap<>();
            final Enumeration<String> headerNames = servletRequest.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                requestHeaders.put(headerName, servletRequest.getHeader(headerName));
            }

            // 获取请求时间戳
            Long requestTimestamp = webContext.getRequestTimestamp();
            String requestTime = null;
            if (requestTimestamp != null) {
                requestTime = DateFormatUtils.format(new Date(requestTimestamp), "yyyy-MM-dd HH:mm:ss.SSS", LOG_TIME_ZONE);
            }

            // 获取响应时间戳
            // 假如响应时间为空的话，获取当前日志的时间戳
            long responseTimestamp = webContext.getResponseTimestamp() != null ? webContext.getResponseTimestamp() : event.getTimeStamp();
            String responseTime = DateFormatUtils.format(new Date(responseTimestamp), "yyyy-MM-dd HH:mm:ss.SSS", LOG_TIME_ZONE);

            // 假如请求时间不为空，则通过请求时间减去响应时间获取接口耗时
            long costTime = requestTimestamp != null ? (responseTimestamp - requestTimestamp) : 0L;
            // 同时设置到旧字段
            builder.costTime(costTime);

            builder.requestDetail(
                    DistributedLogEvent.RequestDetail
                            .builder()
                            .requestTime(requestTime)
                            .requestTimestamp(requestTimestamp)
                            .method(servletRequest.getMethod())
                            .uri(servletRequest.getRequestURI())
                            .query(servletRequest.getQueryString())
                            .headers(requestHeaders)
                            .body(requestBody)
                            .build()
            );
            builder.responseDetail(
                    DistributedLogEvent.ResponseDetail
                            .builder()
                            .responseTime(responseTime)
                            .responseTimestamp(responseTimestamp)
                            .status(webContext.getResponseStatus())
                            .code(webContext.getResponseCode())
                            .costTime(costTime)
                            .body(responseBody)
                            .bodySize(bodySize)
                            .build()
            );
        }

        // 清理 MDC
        MDC.clear();

        StringBuilder result = new StringBuilder(msg.length() + CoreConstants.LINE_SEPARATOR.length());

        // 日志类型不是请求响应日志
        // 并且超过大小限制，就分成多条日志
        if (!LogFlag.REQUEST_RESPONSE.equals(logFlag) && msg.length() > MAX_PRINT_LOG_LENGTH) {
            List<String> strList = getStrList(msg, MAX_PRINT_LOG_LENGTH);
            builder.msg("日志过长，自动切割成" + strList.size() + "份日志");
            result.append(JsonUtils.toJsonString(builder.build())).append(CoreConstants.LINE_SEPARATOR);
            for (int i = 0; i < strList.size(); i++) {
                // 设置默认的flag以及移除耗时
                builder.flag(LogFlag.OTHER.getFlag());
                builder.costTime(null);
                // 增加前缀
                builder.msg("被切割的日志(" + i + ")\n" + strList.get(i));
                result.append(JsonUtils.toJsonString(builder.build())).append(CoreConstants.LINE_SEPARATOR);
            }
        } else {
            builder.msg(msg);
            result.append(JsonUtils.toJsonString(builder.build())).append(CoreConstants.LINE_SEPARATOR);
        }

        return result.toString();
    }

    private boolean isFullOut() {
        String fullOut = MDC.get("fullOut");
        return StringUtils.isNotBlank(fullOut) && Boolean.parseBoolean(fullOut);
    }

    private static LogFlag getLogFlag(ILoggingEvent event) {
        LogFlag logFlag = null;
        List<Marker> markerList = event.getMarkerList();
        if (markerList != null && !markerList.isEmpty()) {
            for (Marker marker : markerList) {
                if (marker instanceof LogFlagBasicMarker logFlagBasicMarker) {
                    logFlag = logFlagBasicMarker.getLogFlag();
                    break;
                }
            }
        }

        if (logFlag == null) {
            logFlag = LogFlag.OTHER;
        }
        return logFlag;
    }


    /**
     * 把原始字符串分割成指定长度的字符串列表
     *
     * @param inputString 原始字符串
     * @param length      指定长度
     */
    @SuppressWarnings("SameParameterValue")
    protected static List<String> getStrList(String inputString, int length) {
        int size = inputString.length() / length;
        if (inputString.length() % length != 0) {
            size += 1;
        }
        return getStrList(inputString, length, size);
    }

    /**
     * 把原始字符串分割成指定长度的字符串列表
     *
     * @param inputString 原始字符串
     * @param length      指定长度
     * @param size        指定列表大小
     */
    protected static List<String> getStrList(String inputString, int length, int size) {
        List<String> list = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            String childStr = substring(inputString, index * length,
                    (index + 1) * length);
            if (StringUtils.isNotBlank(childStr)) {
                list.add(childStr);
            }
        }
        return list;
    }

    /**
     * 分割字符串，如果开始位置大于字符串长度，返回空
     *
     * @param str 原始字符串
     * @param f   开始位置
     * @param t   结束位置
     */
    public static String substring(String str, int f, int t) {
        if (f > str.length()) {
            return null;
        }
        if (t > str.length()) {
            return str.substring(f);
        } else {
            return str.substring(f, t);
        }
    }
}
