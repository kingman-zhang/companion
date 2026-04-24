package com.kingman.companion.framework.security;

/**
 * 当前请求的认证上下文（ThreadLocal）
 * 在拦截器解析 Token 后设置，请求结束后清除
 */
public class AuthContext {

    private static final ThreadLocal<LoginUser> CURRENT_USER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        CURRENT_USER.set(user);
    }

    public static LoginUser get() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }

    public static String getCurrentUserId() {
        LoginUser user = CURRENT_USER.get();
        return user != null ? user.getUserId() : null;
    }
}
