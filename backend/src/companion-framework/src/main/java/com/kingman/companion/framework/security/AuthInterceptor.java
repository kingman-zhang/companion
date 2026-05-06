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

        // 判断是否为公开接口或第三方回调
        boolean isPublic = handlerMethod.hasMethodAnnotation(SkipCheckLoginAuth.class)
                || handlerMethod.getBeanType().isAnnotationPresent(SkipCheckLoginAuth.class)
                || handlerMethod.hasMethodAnnotation(CallbackMethod.class);

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        boolean hasToken = StringUtils.hasText(authHeader) && authHeader.startsWith(TOKEN_PREFIX);

        if (isPublic) {
            // 公开接口：若携带了有效 Token，顺带解析并设置上下文（方便服务读取 userId）
            // 解析失败则忽略，不影响请求继续处理
            if (hasToken) {
                try {
                    AuthContext.set(jwtUtils.parseToken(authHeader.substring(TOKEN_PREFIX.length())));
                } catch (Exception ignored) {}
            }
            return true;
        }

        // 受保护接口：必须携带有效 Token
        if (!hasToken) {
            throw new UserUnauthorizedException();
        }
        AuthContext.set(jwtUtils.parseToken(authHeader.substring(TOKEN_PREFIX.length())));
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
