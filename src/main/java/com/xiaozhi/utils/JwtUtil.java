package com.xiaozhi.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类，用于生成和解析JWT Token
 */
@Component
public class JwtUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    
    // 默认密钥，如果配置文件中没有指定，则使用这个
    private static final String DEFAULT_SECRET = "xiaozhi_jwt_secret_key_must_be_at_least_256_bits_long_for_hs256";
    
    // 从配置文件中读取密钥，如果没有配置则使用默认值
    @Value("${jwt.secret:#{null}}")
    private String configSecret;
    
    // 从配置文件中读取token过期时间(秒)，默认24小时
    @Value("${jwt.expiration:86400}")
    private long expiration;
    
    // 从配置文件中读取token刷新时间(秒)，默认7天
    @Value("${jwt.refresh-expiration:604800}")
    private long refreshExpiration;
    
    /**
     * 生成JWT Token
     * @param userId 用户ID
     * @param username 用户名
     * @return JWT Token字符串
     */
    public String generateToken(Integer userId, String username) {
        return generateToken(userId, username, new HashMap<>());
    }
    
    /**
     * 生成JWT Token，可以添加自定义声明
     * @param userId 用户ID
     * @param username 用户名
     * @param additionalClaims 额外的声明
     * @return JWT Token字符串
     */
    public String generateToken(Integer userId, String username, Map<String, Object> additionalClaims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration * 1000);
        
        // 创建标准声明
        Map<String, Object> claims = new HashMap<>(additionalClaims);
        claims.put("sub", userId.toString());  // subject通常设置为用户ID
        claims.put("username", username);
        claims.put("iat", now.getTime() / 1000);  // 发布时间（秒）
        claims.put("exp", expiryDate.getTime() / 1000);  // 过期时间（秒）
        claims.put("jti", java.util.UUID.randomUUID().toString());  // JWT ID，用于防止重放攻击
        
        // 生成JWT
        return Jwts.builder()
                .setClaims(claims)
                .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * 生成刷新Token
     * @param userId 用户ID
     * @return 刷新Token字符串
     */
    public String generateRefreshToken(Integer userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration * 1000);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId.toString());
        claims.put("iat", now.getTime() / 1000);
        claims.put("exp", expiryDate.getTime() / 1000);
        claims.put("type", "refresh");
        
        return Jwts.builder()
                .setClaims(claims)
                .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * 从Token中获取用户ID
     * @param token JWT Token
     * @return 用户ID
     */
    public Integer getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return Integer.parseInt(claims.getSubject());
    }
    
    /**
     * 从Token中获取用户名
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("username", String.class);
    }
    
    /**
     * 从Token中获取过期时间
     * @param token JWT Token
     * @return 过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return new Date(claims.get("exp", Long.class) * 1000);
    }
    
    /**
     * 从Token中获取所有声明
     * @param token JWT Token
     * @return 声明
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSecretKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    /**
     * 验证Token是否有效
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(getSecretKey())
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("JWT Token已过期: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("JWT Token验证失败: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 检查Token是否过期
     * @param token JWT Token
     * @return 是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 刷新Token
     * @param token 原Token
     * @return 新Token
     */
    public String refreshToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            Integer userId = Integer.parseInt(claims.getSubject());
            String username = claims.get("username", String.class);
            
            // 创建一个新的claims，复制原来的自定义claims
            Map<String, Object> newClaims = new HashMap<>();
            for (Map.Entry<String, Object> entry : claims.entrySet()) {
                // 跳过标准claims
                if (!entry.getKey().equals("sub") && !entry.getKey().equals("iat") && 
                    !entry.getKey().equals("exp") && !entry.getKey().equals("jti") && 
                    !entry.getKey().equals("username")) {
                    newClaims.put(entry.getKey(), entry.getValue());
                }
            }
            
            // 生成新token
            return generateToken(userId, username, newClaims);
        } catch (Exception e) {
            logger.error("刷新Token失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取密钥
     * @return 密钥
     */
    private SecretKey getSecretKey() {
        String secret = configSecret != null ? configSecret : DEFAULT_SECRET;
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * 从Token中获取指定的声明
     * @param token JWT Token
     * @param claimName 声明名称
     * @return 声明值
     */
    public Object getClaimFromToken(String token, String claimName) {
        Claims claims = getClaimsFromToken(token);
        return claims.get(claimName);
    }
    
    /**
     * 生成带有微信信息的Token
     * @param userId 用户ID
     * @param username 用户名
     * @param openid 微信openid
     * @param sessionKey 微信session_key
     * @return JWT Token
     */
    public String generateWxToken(Integer userId, String username, String openid, String sessionKey) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("wx_openid", openid);
        claims.put("wx_session_key", sessionKey);
        return generateToken(userId, username, claims);
    }
}