package com.xiaozhi.server.config;

import com.xiaozhi.utils.RequestContextUtils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
/**
 * 登录限流拦截器
 * <p>
 * 基于 IP 地址对登录、注册、验证码等端点进行请求频率限制，
 * 防止暴力破解和接口滥用。
 * <p>
 * 限流规则：同一 IP 在时间窗口内最多允许指定次数请求，
 * 超出后返回 429 (Too Many Requests)。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** Redis key 前缀 */
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    /** 时间窗口（秒） */
    private static final int WINDOW_SECONDS = 60;

    /** 登录/注册端点：每分钟最多 10 次 */
    private static final int MAX_AUTH_REQUESTS = 10;

    /** 验证码端点：每分钟最多 5 次 */
    private static final int MAX_CAPTCHA_REQUESTS = 5;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String clientIp = RequestContextUtils.getClientIp(request);
        String uri = request.getRequestURI();

        // 根据端点类型选择不同的限流阈值
        int maxRequests = uri.contains("Captcha") ? MAX_CAPTCHA_REQUESTS : MAX_AUTH_REQUESTS;

        String redisKey = RATE_LIMIT_PREFIX + normalizeUri(uri) + ":" + clientIp;

        try {
            Long count = stringRedisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                // 首次请求，设置过期时间
                stringRedisTemplate.expire(redisKey, WINDOW_SECONDS, TimeUnit.SECONDS);
            }

            if (count != null && count > maxRequests) {
                log.warn("请求频率超限 - IP: {}, 端点: {}, 次数: {}/{}", clientIp, uri, count, maxRequests);
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
                return false;
            }
        } catch (Exception e) {
            // Redis 异常时放行，不影响正常使用
            log.error("限流检查异常，已放行: {}", e.getMessage());
        }

        return true;
    }

    /**
     * 规范化 URI 用于 Redis key（去掉特殊字符，统一格式）
     */
    private String normalizeUri(String uri) {
        return uri.replaceAll("[^a-zA-Z0-9/]", "_");
    }
}
