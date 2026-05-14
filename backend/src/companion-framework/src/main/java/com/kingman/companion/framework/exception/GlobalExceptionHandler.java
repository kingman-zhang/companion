package com.kingman.companion.framework.exception;

import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.common.IResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * Service 层只抛异常，所有异常统一在此处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<IResult<Void>> handleApiException(ApiException e) {
        log.warn("业务异常: code={}, message={}", e.getCodeEnum().getCode(), e.getMessage());
        return ResponseEntity.ok(IResult.fail(e.getCodeEnum()));
    }

    @ExceptionHandler(SafetyBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleSafetyBlocked(SafetyBlockedException e) {
        log.warn("安全拦截: triggerType={}", e.getTriggerType());
        Map<String, Object> body = new HashMap<>();
        body.put("code", "SAFETY_BLOCKED");
        body.put("message", e.getMessage());
        body.put("trigger_type", e.getTriggerType());
        body.put("session_cooldown_until", null);
        // HTTP 451 - Unavailable For Legal Reasons
        return ResponseEntity.status(451).body(body);
    }

    @ExceptionHandler(UserUnauthorizedException.class)
    public ResponseEntity<IResult<Void>> handleUnauthorized(UserUnauthorizedException e) {
        log.warn("认证失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(IResult.fail(CodeEnum.UNAUTHORIZED));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<IResult<Map<String, String>>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("参数校验失败: {}", errors);
        IResult<Map<String, String>> result = IResult.fail(CodeEnum.INVALID_REQUEST);
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(ClientDisconnectedException.class)
    public ResponseEntity<Void> handleClientDisconnected(ClientDisconnectedException e) {
        log.info("客户端已断开流式连接: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception e, HttpServletRequest request) {
        log.error("未处理异常", e);
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            log.warn("检测到 SSE 请求异常，跳过通用 JSON 错误响应，避免二次写回失败");
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(IResult.fail(CodeEnum.INTERNAL_SERVER_ERROR));
    }
}
