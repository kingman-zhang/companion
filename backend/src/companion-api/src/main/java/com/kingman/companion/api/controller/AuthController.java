package com.kingman.companion.api.controller;

import com.kingman.companion.framework.annotation.SkipCheckLoginAuth;
import com.kingman.companion.framework.common.IResult;
import com.kingman.companion.module.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 * POST /api/v1/auth/wx-login — 微信 OpenID 静默登录
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@SkipCheckLoginAuth
public class AuthController {

    private final UserService userService;

    /**
     * 微信静默登录：用 code 换 JWT Token
     */
    @PostMapping("/wx-login")
    public IResult<WxLoginResp> wxLogin(@Valid @RequestBody WxLoginReq req) {
        String token = userService.loginByCode(req.code());
        return IResult.success(new WxLoginResp(token, 86400000L));
    }

    // ── Inner types ──────────────────────────────────────────────────────────

    record WxLoginReq(@NotBlank(message = "code 不能为空") String code) {}

    record WxLoginResp(String token, long expiresIn) {}
}
