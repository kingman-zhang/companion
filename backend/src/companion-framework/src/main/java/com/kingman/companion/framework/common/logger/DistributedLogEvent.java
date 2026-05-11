package com.kingman.companion.framework.common.logger;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * 分布式链路日志消息实体
 *
 * @author kingman
 */
@Getter
@Setter
@Builder
public class DistributedLogEvent implements Serializable {

    /**
     * 日志产生时间(yyyy-MM-dd hh:mm:ss.SSS)
     */
    private String createTime;

    /**
     * 日志产生时间戳
     */
    private Long createTimestamp;

    /**
     * traceId，每个请求唯一
     */
    private String traceId;

    /**
     * 日志级别 INFO DEBUG ERROR
     */
    private String level;

    /**
     * 日志所属系统
     */
    private String sysName;

    /**
     * 产生日志的服务器IP
     */
    private String ip;

    /**
     * 用户请求ip
     */
    private String clientIp;

    /**
     * 日志标识
     */
    private Integer flag;

    /**
     * 产生日志的类
     */
    private String className;

    /**
     * 产生日志的方法
     */
    private String methodName;

    /**
     * 当前线程名称
     */
    private String thread;

    /**
     * 日志信息
     */
    private String msg;

    /**
     * 一次执行耗时（用于打印响应日志）
     */
    private Long costTime;

    /**
     * 异常名
     */
    private String errName;

    /**
     * 接口名
     */
    private String api;
    /**
     * 路由接口名
     */
    private String routeApi;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 设备指纹
     */
    private String fingerprint;

    /**
     * APP版本
     */
    private String appVersion;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 客户端系统
     */
    private String clientOs;

    /**
     * 语言
     */
    private String language;

    /**
     * 系统语言
     */
    private String systemLanguage;

    /**
     * 地区
     */
    private String location;

    /**
     * ISO国家码
     */
    private String countryIsoCode;

    /**
     * IP 所属的ISO国家码
     */
    private String clientIpCountryIsoCode;

    /**
     * SIM卡的ISO国家码
     */
    private String simCountryIsoCode;

    /**
     * APP商店code
     */
    private String storeCode;
    
    /**
     * 包名
     */
    private String packageNo;

    /**
     * 请求详细信息
     * <p>
     * 只有在 {@link LogFlag#REQUEST_RESPONSE} 的时候才会写入
     */
    private RequestDetail requestDetail;

    /**
     * 响应详细信息
     * <p>
     * 只有在 {@link LogFlag#REQUEST_RESPONSE} 的时候才会写入
     */
    private ResponseDetail responseDetail;

    /**
     * 请求详情
     */
    @Getter
    @Setter
    @Builder
    public static class RequestDetail implements Serializable {

        /**
         * 请求时间(yyyy-MM-dd hh:mm:ss.SSS)
         */
        private String requestTime;

        /**
         * 请求时间的时间戳
         */
        private Long requestTimestamp;

        /**
         * 请求方法
         */
        private String method;

        /**
         * 请求URI
         */
        private String uri;

        /**
         * 请求参数
         */
        private String query;

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
         * 响应时间(yyyy-MM-dd hh:mm:ss.SSS)
         */
        private String responseTime;

        /**
         * 响应时间的时间戳
         */
        private Long responseTimestamp;

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

        /**
         * 响应体大小
         */
        private Long bodySize;
    }
}
