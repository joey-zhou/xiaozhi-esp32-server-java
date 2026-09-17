package com.xiaozhi.file;

import com.xiaozhi.common.web.LocalFileUrlPolicy;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
/**
 * 本地存储静态目录的访问校验：受保护目录下只有带未过期签名的请求才放行，其余路径直接通过
 */
@Slf4j
@Component
public class LocalFileAccessInterceptor implements HandlerInterceptor {

    @Resource
    private LocalFileUrlPolicy localFileUrlPolicy;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = requestPath(request);
        boolean allowed;
        try {
            allowed = localFileUrlPolicy.verify(path, request.getParameter("exp"), request.getParameter("sig"));
        } catch (RuntimeException e) {
            log.error("文件访问签名校验异常: {}", path, e);
            allowed = false;
        }
        if (allowed) {
            return true;
        }
        log.warn("文件访问签名无效，已拒绝: {}", path);
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"访问地址无效或已过期\"}");
        return false;
    }

    private static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri.startsWith("/") ? uri.substring(1) : uri;
    }
}
