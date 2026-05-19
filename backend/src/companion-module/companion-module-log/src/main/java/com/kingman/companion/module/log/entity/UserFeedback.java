package com.kingman.companion.module.log.entity;

import com.kingman.companion.framework.common.AbstractBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 用户反馈与联系记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "user_feedback")
public class UserFeedback extends AbstractBaseEntity {

    /** 反馈类型：BUG / SUGGESTION / COOPERATION */
    private String type;

    /** 反馈内容 */
    private String content;

    /** 联系方式（邮箱 / 微信 / 手机号等） */
    private String contact;

    /** 提交用户 ID（匿名时为空） */
    private String userId;

    /** 当前小程序页面路径，便于排查问题 */
    private String sourcePage;
}
