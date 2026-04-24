package com.kingman.companion.framework.exception;

/**
 * 认证失败异常（HTTP 401）
 */
public class UserUnauthorizedException extends RuntimeException {

    public UserUnauthorizedException() {
        super("未登录或 Token 已过期");
    }

    public UserUnauthorizedException(String message) {
        super(message);
    }
}
