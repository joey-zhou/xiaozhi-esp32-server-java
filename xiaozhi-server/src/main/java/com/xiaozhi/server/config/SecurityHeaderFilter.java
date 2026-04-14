package com.xiaozhi.server.config;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 安全响应头过滤器，为所有 HTTP 响应添加标准安全头。
 * <p>
 * 在过滤器链的早期执行，确保无论下游处理结果如何，
 * 安全头都会被添加到响应中。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 防止浏览器进行 MIME 类型嗅探
        response.setHeader("X-Content-Type-Options", "nosniff");

        // 禁止页面被嵌入 iframe，防止点击劫持
        response.setHeader("X-Frame-Options", "DENY");

        // 控制 Referer 信息的发送策略
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // 禁用旧版 XSS 过滤器，依赖 CSP 防护
        response.setHeader("X-XSS-Protection", "0");

        // 限制浏览器功能的访问权限
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

        filterChain.doFilter(request, response);
    }
}
