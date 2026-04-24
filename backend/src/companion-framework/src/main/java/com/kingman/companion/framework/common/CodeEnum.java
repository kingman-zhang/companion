package com.kingman.companion.framework.common;

import lombok.Getter;

/**
 * 业务错误码枚举
 * 格式：HTTP 状态码前缀 + 3位序号
 */
@Getter
public enum CodeEnum {

    // 成功
    SUCCESS(200, "success"),

    // 4xx 客户端错误
    MISSING_REQUIRED_FIELD(400001, "缺少必填字段"),
    INVALID_REQUEST(400002, "请求参数错误"),
    UNAUTHORIZED(401001, "未登录或 Token 已过期"),
    PLAN_PAYWALL(402001, "需要付费解锁"),
    NOT_FOUND(404001, "资源不存在"),
    LOG_ALREADY_SUBMITTED(409001, "今日日志已提交"),
    INVALID_ENUM_VALUE(422001, "字段值非法"),
    FREE_TIER_LIMIT_REACHED(429001, "超出免费层上限"),
    SAFETY_BLOCKED(451001, "检测到安全风险，已中断当前操作"),

    // 5xx 服务端错误
    INTERNAL_SERVER_ERROR(500001, "服务器内部错误"),
    AI_SERVICE_UNAVAILABLE(503001, "AI 服务暂时不可用");

    private final int code;
    private final String message;

    CodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
