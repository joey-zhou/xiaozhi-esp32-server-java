package com.xiaozhi.common.config;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 运维端点（Actuator、接口文档）的访问控制：一律要求登录态。
 * <p>
 * 这些路径不在 {@code /api/**} 之下，业务鉴权拦截器覆盖不到，与设备/业务端口又是同一个端口对外。
 * 用过滤器而不是 MVC 拦截器：Actuator 的端点挂在自己的 HandlerMapping 上，
 * {@code addInterceptors} 注册的拦截器不会作用到它们。server 与 dialogue 都会装配本配置。
 */
@Configuration
public class OpsEndpointAccessConfig {

    /** Servlet 的 {@code /x/*} 同时匹配 /x 与 /x/... ，端点根路径不必再单列 */
    private static final String[] OPS_URL_PATTERNS = {
            "/actuator/*",
            "/v3/api-docs/*", "/v3/api-docs.yaml",
            "/swagger-ui/*", "/swagger-ui.html",
            "/swagger-resources/*",
            "/doc.html"
    };

    /** 必须排在 Spring 的 RequestContextFilter（-105）之后：Sa-Token 取登录态要用它绑定的当前请求 */
    private static final int FILTER_ORDER = -100;

    @Bean
    public FilterRegistrationBean<OpsEndpointAccessFilter> opsEndpointAccessFilter() {
        FilterRegistrationBean<OpsEndpointAccessFilter> registration =
                new FilterRegistrationBean<>(new OpsEndpointAccessFilter());
        registration.addUrlPatterns(OPS_URL_PATTERNS);
        registration.setOrder(FILTER_ORDER);
        return registration;
    }

    public static class OpsEndpointAccessFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isLoggedIn()) {
                filterChain.doFilter(request, response);
                return;
            }
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未授权访问\"}");
        }

        /** 未登录属正常情况，不能让取登录态的异常冒到调用方 */
        private boolean isLoggedIn() {
            try {
                return StpUtil.getLoginIdDefaultNull() != null;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
