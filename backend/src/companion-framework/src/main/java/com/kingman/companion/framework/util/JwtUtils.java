package com.kingman.companion.framework.util;

import com.kingman.companion.framework.exception.UserUnauthorizedException;
import com.kingman.companion.framework.security.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 */
@Slf4j
@Component
public class JwtUtils {

    @Value("${companion.jwt.secret:companion-secret-key-must-be-at-least-32-chars}")
    private String secret;

    @Value("${companion.jwt.expiration:86400000}")
    private long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(LoginUser loginUser) {
        return Jwts.builder()
                .subject(loginUser.getUserId())
                .claim("username", loginUser.getUsername())
                .claim("subscriptionTier", loginUser.getSubscriptionTier())
                .claim("packageNo", loginUser.getPackageNo())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public LoginUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return LoginUser.builder()
                    .userId(claims.getSubject())
                    .username(claims.get("username", String.class))
                    .subscriptionTier(claims.get("subscriptionTier", String.class))
                    .packageNo(claims.get("packageNo", String.class))
                    .build();
        } catch (JwtException e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            throw new UserUnauthorizedException("Token 无效或已过期");
        }
    }
}
