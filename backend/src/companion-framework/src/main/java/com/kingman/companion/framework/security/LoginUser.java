package com.kingman.companion.framework.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录用户信息，存储在 ThreadLocal 中
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    private String userId;
    private String username;
    /** 订阅等级：free / premium */
    private String subscriptionTier;
    /** 租户标识 */
    private String packageNo;
}
