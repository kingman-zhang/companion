package com.kingman.companion.framework.exception;

/**
 * SSE / 流式响应过程中，客户端主动断开连接。
 *
 * <p>这属于可预期的中止场景，不应再走统一 500 异常响应。
 */
public class ClientDisconnectedException extends RuntimeException {

    public ClientDisconnectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
