package com.kingman.companion.framework.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.kingman.companion.framework.util.BytecodeChecker;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.UUID;

/**
 * 分布式请求上下文
 *
 * @author kingman
 */
@Getter
@Setter
@Slf4j
public class DistributedContext implements Serializable {

    private static final DistributedContextThreadLocal THREAD_LOCAL = new DistributedContextThreadLocal();

    /**
     * 链路ID
     */
    private String traceId;

    /**
     * 请求Ip
     */
    private String clientIp;

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
     * 设备型号
     */
    private String deviceModel;

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
     * APP语言
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
     * 时区
     */
    private String timeZone;

    /**
     * APP商店code
     */
    private String storeCode;

    /**
     * 渠道
     */
    private String channel;
    
    /**
     * 包名
     */
    private String packageNo;


    private DistributedContext() {
    }

    public static DistributedContext newInstance() {
        return newInstance(null);
    }

    public static DistributedContext newInstance(String traceId) {
        DistributedContext context = new DistributedContext();
        if (StringUtils.isNotBlank(traceId)) {
            context.traceId = traceId;
        } else {

            // 使用匿名对象
            var ref = new Object() {
                String traceId = null;
            };

            // 整合 ARMS
            if (BytecodeChecker.isExist("com.alibaba.arms.tracing.Tracer")) {
                ref.traceId = com.alibaba.arms.tracing.Tracer.builder().getSpan().getTraceId();
            }

            // 整合skywalking获取
            if (StringUtils.isBlank(ref.traceId)) {
                if (BytecodeChecker.isExist("org.apache.skywalking.apm.toolkit.trace.TraceContext")) {
                    String skywalkingTraceId = org.apache.skywalking.apm.toolkit.trace.TraceContext.traceId();
                    if (isSkywalkingTraceId(skywalkingTraceId)) {
                        ref.traceId = skywalkingTraceId;
                    }
                }
            }

            // 兜底使用UUID
            if (StringUtils.isBlank(ref.traceId)) {
                ref.traceId = UUID.randomUUID().toString();
            }
            context.traceId = ref.traceId;
        }
        setContext(context);
        return context;
    }

    public static DistributedContext getContext() {
        return getContext(true);
    }

    public static DistributedContext getContext(boolean isCreateIfNotExist) {
        DistributedContext context = THREAD_LOCAL.get();
        if (context != null) {
            return context;
        }
        if (isCreateIfNotExist) {
            context = newInstance();
        }
        return context;
    }

    public static void setContext(DistributedContext context) {
        THREAD_LOCAL.set(context);
    }

    public static void removeContext() {
        THREAD_LOCAL.remove();
    }

    /**
     * 假如是判断能拿到 traceId 或者是 traceId 不为 N/A，就设置 traceId 为 logId
     * traceId 的格式 b11efbb9ca6d435daefa1dc286d79ee1.110.16324561628630013,所以假如不包含2个.的，肯定不是 traceId
     */
    private static boolean isSkywalkingTraceId(String traceId) {
        return StringUtils.isNotBlank(traceId)
                && !"N/A".equals(traceId)
                && (traceId.contains(".") && traceId.split("\\.").length == 3);
    }

    /**
     * 继承TTL用于实现跨线程传递上下文
     *
     * @author kingman
     */
    @Slf4j
    private static class DistributedContextThreadLocal extends TransmittableThreadLocal<DistributedContext> {

        @Override
        public DistributedContext copy(DistributedContext parentValue) {
            // 默认情况下是直接传递上下文的对象
            // 之后看看是否需要重新复制一个新的上下文用于隔离
            return parentValue;
        }
    }
}
