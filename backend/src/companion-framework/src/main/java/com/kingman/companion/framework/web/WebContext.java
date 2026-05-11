package com.kingman.companion.framework.web;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.kingman.companion.framework.util.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author kingman
 */
@SuppressWarnings("unused")
public class WebContext extends ConcurrentHashMap<String, Object> {

    /**
     * 为空的缓存标识
     */
    protected final static String NA = "_N/A_";

    /**
     * DistributedContext 转化为 WebContext
     */
    private static final ThreadLocal<WebContext> THREAD_LOCAL = TransmittableThreadLocal.withInitial(() -> newInstance(null, null));

    private final ConcurrentHashMap<Type, Object> requestDataCache = new ConcurrentHashMap<>();

    @Getter
    private final HttpServletRequest servletRequest;
    @Getter
    private final HttpServletResponse servletResponse;


    private WebContext(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        this.servletRequest = servletRequest;
        this.servletResponse = servletResponse;
        // 设置当前时间为开始时间
        this.setRequestTimestamp(System.currentTimeMillis());
    }

    public static WebContext newInstance(
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        WebContext webContext = new WebContext(servletRequest, servletResponse);
        THREAD_LOCAL.set(webContext);
        return webContext;
    }


    public static WebContext getContext() {
        return THREAD_LOCAL.get();
    }

    public static void removeContext() {
        THREAD_LOCAL.remove();
    }

    public Long getRequestTimestamp() {
        return getValue("requestTimestamp");
    }

    public void setRequestTimestamp(Long requestTimestamp) {
        setValue("requestTimestamp", requestTimestamp);
    }

    public Long getResponseTimestamp() {
        return getValue("responseTimestamp");
    }

    public void setResponseTimestamp(Long responseTimestamp) {
        setValue("responseTimestamp", responseTimestamp);
    }

    public String getRequestUri() {
        String requestUri = getValue("requestURI");
        if (StringUtils.isBlank(requestUri) && servletRequest != null) {
            requestUri = servletRequest.getRequestURI();
            this.setRequestUri(requestUri);
        }
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        setValue("requestURI", requestUri);
    }

    public Integer getResponseStatus() {
        return getValue("responseStatus");
    }

    public void setResponseStatus(Integer responseStatus) {
        setValue("responseStatus", responseStatus);
    }

    public Integer getResponseCode() {
        return getValue("responseCode");
    }

    public void setResponseCode(Integer responseCode) {
        setValue("responseCode", responseCode);
    }

    public Long getBodySize() {
        return getValue("responseBodySize");
    }

    public void setBodySize(Long bodySize) {
        setValue("responseBodySize", bodySize);
    }

    public String getRequestData() throws IOException {
        String requestData = (String) get("requestData");
        if (requestData == null && servletRequest != null) {
            // 先判断上下文中是否存在缓存，没有的话则直接重新读流获取
            if (servletRequest instanceof RequestWrapper) {
                requestData = IOUtils.toString(servletRequest.getReader());
                this.setRequestData(requestData);
            }
        }
        return requestData;
    }

    public void setRequestData(String requestData) {
        requestDataCache.clear();
        setValue("requestData", requestData);
    }

    public <T> T getRequestData(Type type) throws IOException {
        T obj;
        if (type != null) {
            Object cache = requestDataCache.get(type);
            if (cache == null) {
                String requestData = getRequestData();
                if (StringUtils.isNotBlank(requestData)) {
                    obj = JsonUtils.toJavaObject(requestData, type);
                    requestDataCache.put(type, obj);
                } else {
                    obj = null;
                    requestDataCache.put(type, NA);
                }
            } else if (NA.equals(cache)) {
                // 缓存中记录为空
                obj = null;
            } else {
                // 返回缓存
                //noinspection unchecked
                obj = (T) cache;
            }
        } else {
            obj = null;
        }
        return obj;
    }

    public <T> void setRequestData(T requestData, Class<T> clz) {
        requestDataCache.clear();
        requestDataCache.put(clz, requestData);
        put("requestData", JsonUtils.toJsonString(requestData));
    }

    private <T> void setValue(String key, T value) {
        if (value != null) {
            put(key, value);
        } else {
            remove(key);
        }
    }

    private <T> T getValue(String key) {
        Object value = get(key);
        if (value != null) {
            if (NA.equals(value)) {
                return null;
            }
        }
        //noinspection unchecked
        return (T) value;
    }


    /**
     * 请求详情
     */
    @Getter
    @Setter
    @Builder
    public static class RequestDetail implements Serializable {

        /**
         * 请求时间的时间戳
         */
        private String requestTimestamp;

        /**
         * 请求方法
         */
        private String method;

        /**
         * 请求URI
         */
        private String uri;

        /**
         * 请求的Header
         */
        private Map<String, String> headers;

        /**
         * 请求的body
         */
        private String body;
    }

    /**
     * 响应详情
     */
    @Getter
    @Setter
    @Builder
    public static class ResponseDetail implements Serializable {

        /**
         * 响应时间的时间戳
         */
        private String responseTimestamp;

        /**
         * 响应状态码
         */
        private Integer status;

        /**
         * 响应业务状态码
         */
        private Integer code;

        /**
         * 响应耗时
         */
        private Long costTime;

        /**
         * 响应的body
         */
        private String body;
    }
}
