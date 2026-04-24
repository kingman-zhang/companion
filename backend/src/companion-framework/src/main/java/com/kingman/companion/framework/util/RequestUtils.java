package com.kingman.companion.framework.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求元数据工具类
 */
public class RequestUtils {

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR", "HTTP_X_FORWARDED", "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP", "HTTP_FORWARDED_FOR", "HTTP_FORWARDED",
            "HTTP_VIA", "REMOTE_ADDR"
    };

    private RequestUtils() {
    }

    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("当前线程不在 Web 请求上下文中");
        }
        return attributes.getRequest();
    }

    /**
     * 获取客户端真实 IP
     */
    public static String getClientIp() {
        HttpServletRequest request = getRequest();
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能包含多个 IP，取第一个
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 获取设备 ID（从请求头读取）
     */
    public static String getDeviceId() {
        return getRequest().getHeader("X-Device-Id");
    }

    /**
     * 获取用户代理
     */
    public static String getUserAgent() {
        return getRequest().getHeader("User-Agent");
    }
}
