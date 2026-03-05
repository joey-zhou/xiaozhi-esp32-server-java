package com.xiaozhi.security.filter;

import com.xiaozhi.security.AnonymousAccessManager;
import com.xiaozhi.security.CustomUserDetailsService;
import com.xiaozhi.security.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 * 从请求头中提取 JWT 令牌并进行验证
 *
 * @author Joey
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Autowired
    @Lazy
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    @Lazy
    private CustomUserDetailsService userDetailsService;
    @Autowired
    @Lazy
    private AnonymousAccessManager anonymousAccessManager;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        log.debug("处理请求：{} {}", request.getMethod(), requestUri);

        // 检查是否为匿名访问路径
        if (anonymousAccessManager.isAnonymousPath(requestUri)) {
            log.debug("匿名访问路径，跳过认证：{}", requestUri);
            filterChain.doFilter(request, response);
            return;
        }

        // 从请求头获取 JWT 令牌
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token)) {
            try {
                // 验证令牌并设置认证信息
                Integer userId = jwtTokenProvider.getUserIdFromToken(token);
                if (userId != null) {
                    UserDetails userDetails = userDetailsService.loadUserByUserId(userId);

                    if (jwtTokenProvider.validateToken(token, userDetails)) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                        );

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("设置用户认证信息：{}", userDetails.getUsername());
                    }
                }
            } catch (Exception e) {
                log.error("无法设置用户认证信息：{}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头获取 JWT 令牌
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        // 优先从 Authorization 头获取
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // 兼容从 X-Access-Token 头获取
        String accessToken = request.getHeader("X-Access-Token");
        if (StringUtils.hasText(accessToken)) {
            return accessToken;
        }

        // 兼容从 token 参数获取
        String token = request.getParameter("token");
        if (StringUtils.hasText(token)) {
            return token;
        }

        return null;
    }
}
