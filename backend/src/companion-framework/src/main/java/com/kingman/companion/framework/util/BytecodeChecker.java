package com.kingman.companion.framework.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author kingman
 */
public class BytecodeChecker {

    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();

    /**
     * 检查类是否存在
     *
     * @param className 类名
     * @return 存在返回true，否则返回false
     */
    public static boolean isExist(String className) {
        if (CACHE.containsKey(className)) {
            return CACHE.get(className);
        }
        boolean result = true;
        try {
            // 尝试加载你想要检查的类
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            // 不存在
            result = false;
        }
        CACHE.put(className, result);
        return result;
    }

}
