package com.kingman.companion.framework.util;

import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 获取系统信息的工具类
 *
 * @author kingman
 */
public abstract class SystemUtils {

    private SystemUtils() {
    }

    public static final String ENV_KEY_LOCAL_IP = "LOCAL_IP";
    public static final String ENV_KEY_APP_NAME = "APP_NAME";

    public final static String DEFAULT_LOCAL_IP = "";
    public final static String DEFAULT_APP_NAME = "";

    private static final String LOCAL_IP;
    private static final String APP_NAME;

    static {

        String localIp = System.getenv(ENV_KEY_LOCAL_IP);
        if (StringUtils.isNotBlank(localIp)) {
            LOCAL_IP = localIp;
        } else {
            String hostAddress = DEFAULT_LOCAL_IP;
            try {
                hostAddress = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                // 忽略
            }
            LOCAL_IP = hostAddress;
        }

        String systemName = System.getenv(ENV_KEY_APP_NAME);
        if (StringUtils.isNotBlank(systemName)) {
            APP_NAME = systemName;
        } else {
            systemName = (String) System.getProperties().get(ENV_KEY_APP_NAME);
            if (StringUtils.isNotBlank(systemName)) {
                APP_NAME = systemName;
            } else {
                APP_NAME = DEFAULT_APP_NAME;
            }
        }
    }

    public static String getAppName() {
        return APP_NAME;
    }

    public static String getLocalIp() {
        return LOCAL_IP;
    }

}