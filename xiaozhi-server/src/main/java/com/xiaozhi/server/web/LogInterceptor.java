package com.xiaozhi.server.web;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.utils.RequestContextUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
/**
 * 系统日志拦截器
 */
@Slf4j
@Component
public class LogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTRIBUTE = LogInterceptor.class.getName() + ".startTime";
    private static final String HANDLER_ATTRIBUTE = LogInterceptor.class.getName() + ".handler";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (request.getAttribute(START_TIME_ATTRIBUTE) == null) {
            request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        }
        if (handler instanceof HandlerMethod handlerMethod) {
            request.setAttribute(
                HANDLER_ATTRIBUTE,
                handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName()
            );
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return;
        }

        Object startTime = request.getAttribute(START_TIME_ATTRIBUTE);
        long costMs = startTime instanceof Long value ? System.currentTimeMillis() - value : -1L;
        Object userId = currentUserId();
        String requestPath = buildRequestPath(request);
        String handlerName = (String) request.getAttribute(HANDLER_ATTRIBUTE);
        String clientIp = RequestContextUtils.getClientIp(request);

        if (ex != null || response.getStatus() >= 500) {
            log.error(
                "HTTP {} {} -> status={} cost={}ms ip={} userId={} handler={}",
                request.getMethod(),
                requestPath,
                response.getStatus(),
                costMs,
                clientIp,
                userId,
                handlerName,
                ex
            );
            return;
        }

        if (response.getStatus() >= 400) {
            log.warn(
                "HTTP {} {} -> status={} cost={}ms ip={} userId={} handler={}",
                request.getMethod(),
                requestPath,
                response.getStatus(),
                costMs,
                clientIp,
                userId,
                handlerName
            );
            return;
        }

        /* log.debug(
            "HTTP {} {} -> status={} cost={}ms ip={} userId={} handler={}",
            request.getMethod(),
            requestPath,
            response.getStatus(),
            costMs,
            clientIp,
            userId,
            handlerName
        ); */
    }

    private Object currentUserId() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String buildRequestPath(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + queryString;
    }
}
