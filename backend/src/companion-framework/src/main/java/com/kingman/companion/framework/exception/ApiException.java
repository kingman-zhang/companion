package com.kingman.companion.framework.exception;

import com.kingman.companion.framework.common.CodeEnum;
import lombok.Getter;

/**
 * 前端业务异常（HTTP 200 返回，业务错误码标识失败）
 */
@Getter
public class ApiException extends RuntimeException {

    private final CodeEnum codeEnum;

    public ApiException(CodeEnum codeEnum) {
        super(codeEnum.getMessage());
        this.codeEnum = codeEnum;
    }

    public ApiException(CodeEnum codeEnum, String detail) {
        super(detail);
        this.codeEnum = codeEnum;
    }
}
