package com.kingman.companion.framework.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;

/**
 * 统一响应包装器
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IResult<T> {

    private int code;
    private String message;
    private T data;
    private long timestamp;

    private IResult() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    public static <T> IResult<T> success() {
        IResult<T> result = new IResult<>();
        result.code = 200;
        result.message = "success";
        return result;
    }

    public static <T> IResult<T> success(T data) {
        IResult<T> result = new IResult<>();
        result.code = 200;
        result.message = "success";
        result.data = data;
        return result;
    }

    public static <T> IResult<T> fail(CodeEnum codeEnum) {
        IResult<T> result = new IResult<>();
        result.code = codeEnum.getCode();
        result.message = codeEnum.getMessage();
        return result;
    }

    public static <T> IResult<T> fail(int code, String message) {
        IResult<T> result = new IResult<>();
        result.code = code;
        result.message = message;
        return result;
    }
}
