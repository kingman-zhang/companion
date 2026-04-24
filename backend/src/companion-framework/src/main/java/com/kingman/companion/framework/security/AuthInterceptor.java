package com.kingman.companion.framework.security;

import com.kingman.companion.framework.annotation.CallbackMethod;
import com.kingman.companion.framework.annotation.SkipCheckLoginAuth;
import com.kingman.companion.framework.exception.UserUnauthorizedException;
import com.kingman.companion.framework.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * JWT 登录鉴权拦截器
 */
@Slf4j
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 跳过公开接口和第三方回调
        if (handlerMethod.hasMethodAnnotation(SkipCheckLoginAuth.class)
                || handlerMethod.getBeanType().isAnnotationPresent(SkipCheckLoginAuth.class)
                || handlerMethod.hasMethodAnnotation(CallbackMethod.class)) {
            return true;
        }

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(TOKEN_PREFIX)) {
            throw new UserUnauthorizedException();
        }

        String token = authHeader.substring(TOKEN_PREFIX.length());
        LoginUser loginUser = jwtUtils.parseToken(token);
        AuthContext.set(loginUser);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }
}
