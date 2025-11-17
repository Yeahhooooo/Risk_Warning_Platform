package com.riskwarning.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 用于生成、解析和验证JWT令牌
 */
@Component
@Slf4j
public class JwtUtils {

    /**
     * 签名密钥
     * 在生产环境中应该从配置文件中读取
     */
    private static final String SECRET_KEY = "riskWarningPlatformSecretKey2024ForJWTTokenGeneration";

    /**
     * 令牌过期时间（毫秒）
     * 默认24小时
     */
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000;

    /**
     * 生成JWT令牌
     *
     * @param userId 用户ID
     * @param email 用户邮箱
     * @param fullName 用户全名
     * @return JWT令牌字符串
     */
    public String generateToken(Long userId, String email, String fullName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("fullName", fullName);

        return createToken(claims, email);
    }

    /**
     * 创建JWT令牌
     *
     * @param claims 声明信息
     * @param subject 主题（通常是用户标识）
     * @return JWT令牌字符串
     */
    private String createToken(Map<String, Object> claims, String subject) {
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
        byte[] secretKeyBytes = DatatypeConverter.parseBase64Binary(SECRET_KEY);
        Key signingKey = new SecretKeySpec(secretKeyBytes, signatureAlgorithm.getJcaName());

        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date expiration = new Date(nowMillis + EXPIRATION_TIME);

        JwtBuilder builder = Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(signatureAlgorithm, signingKey);

        return builder.compact();
    }

    /**
     * 解析JWT令牌
     *
     * @param token JWT令牌
     * @return 声明信息
     */
    public Claims parseToken(String token) {
        if(token == null) return null;
        if(token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            return Jwts.parser()
                    .setSigningKey(DatatypeConverter.parseBase64Binary(SECRET_KEY))
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    /**
     * 验证JWT令牌是否有效
     *
     * @param token JWT令牌
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !isTokenExpired(claims);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查令牌是否过期
     *
     * @param claims 声明信息
     * @return 是否过期
     */
    private boolean isTokenExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration.before(new Date());
    }

    /**
     * 从令牌中获取用户ID
     *
     * @param token JWT令牌
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.get("userId").toString());
    }

}
