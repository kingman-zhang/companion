package com.kingman.companion.module.user.service;

/**
 * 用户服务
 */
public interface UserService {

    /**
     * 使用微信 code 换取 JWT Token，首次登录时自动创建用户
     *
     * @param code 微信小程序 wx.login() 返回的临时登录码
     * @return JWT Token 字符串
     */
    String loginByCode(String code);
}
