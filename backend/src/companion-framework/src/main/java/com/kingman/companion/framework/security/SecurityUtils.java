package com.kingman.companion.framework.security;

import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.UserUnauthorizedException;

/**
 * 安全工具类
 */
public class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户，未登录则抛异常
     */
    public static LoginUser getCurrentUser() {
        LoginUser user = AuthContext.get();
        if (user == null) {
            throw new UserUnauthorizedException();
        }
        return user;
    }

    /**
     * 获取当前用户 ID，未登录则抛异常
     */
    public static String getCurrentUserId() {
        return getCurrentUser().getUserId();
    }

    /**
     * 获取当前用户，不强制要求登录（公开接口使用）
     */
    public static LoginUser getCurrentUserOrNull() {
        return AuthContext.get();
    }
}
