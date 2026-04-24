package com.kingman.companion.module.rewrite.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 消息改写请求体
 */
@Data
public class RewriteReq {

    private String sessionId;

    @NotBlank(message = "原始消息不能为空")
    @Size(min = 10, max = 1000, message = "原始消息长度须在 10-1000 字之间")
    private String originalMessage;
}
