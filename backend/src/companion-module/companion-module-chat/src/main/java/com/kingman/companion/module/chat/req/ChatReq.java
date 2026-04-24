package com.kingman.companion.module.chat.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送聊天消息请求体
 */
@Data
public class ChatReq {

    @NotBlank(message = "session_id 不能为空")
    private String sessionId;

    @NotBlank(message = "消息内容不能为空")
    @Size(min = 1, max = 2000, message = "消息长度须在 1-2000 字之间")
    private String content;
}
