package com.kingman.companion.module.user.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import com.kingman.companion.framework.security.LoginUser;
import com.kingman.companion.framework.util.DistributeID;
import com.kingman.companion.framework.util.JwtUtils;
import com.kingman.companion.module.user.config.WxAuthProperties;
import com.kingman.companion.module.user.entity.User;
import com.kingman.companion.module.user.repository.UserRepository;
import com.kingman.companion.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 用户服务实现 — 微信静默登录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final WxAuthProperties wxAuthProperties;
    private final RestTemplate restTemplate;

    @Override
    public String loginByCode(String code) {
        // 1. 调用微信 code2Session 接口
        String url = UriComponentsBuilder.fromHttpUrl(wxAuthProperties.getCode2SessionUrl())
                .queryParam("appid", wxAuthProperties.getAppId())
                .queryParam("secret", wxAuthProperties.getAppSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();

        WxCode2SessionResp wxResp;
        try {
            wxResp = restTemplate.getForObject(url, WxCode2SessionResp.class);
        } catch (Exception e) {
            log.error("调用微信 code2Session 接口失败: {}", e.getMessage(), e);
            throw new ApiException(CodeEnum.WX_LOGIN_FAILED);
        }

        // 2. 校验响应
        if (wxResp == null
                || !StringUtils.hasText(wxResp.openid())
                || (wxResp.errcode() != null && wxResp.errcode() != 0)) {
            log.error("微信登录失败: errcode={}, errmsg={}",
                      wxResp != null ? wxResp.errcode() : "null",
                      wxResp != null ? wxResp.errmsg() : "null");
            throw new ApiException(CodeEnum.WX_LOGIN_FAILED);
        }

        // 3. 查找或创建用户（upsert）
        User user = userRepository.findByOpenId(wxResp.openid())
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setId(DistributeID.generate());
                    newUser.setOpenId(wxResp.openid());
                    return userRepository.save(newUser);
                });

        // 4. 构建 LoginUser 并生成 JWT
        LoginUser loginUser = LoginUser.builder()
                .userId(user.getId())
                .subscriptionTier(user.getSubscriptionTier())
                .build();

        return jwtUtils.generateToken(loginUser);
    }

    /**
     * 微信 code2Session 响应结构
     */
    private record WxCode2SessionResp(
            @JsonProperty("openid") String openid,
            @JsonProperty("session_key") String sessionKey,
            @JsonProperty("unionid") String unionid,
            @JsonProperty("errcode") Integer errcode,
            @JsonProperty("errmsg") String errmsg
    ) {}
}
