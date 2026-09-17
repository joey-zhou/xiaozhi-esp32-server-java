package com.xiaozhi.server.web;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.utils.RequestContextUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
/**
 * 系统日志拦截器
 */
@Slf4j
@Component
public class LogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTRIBUTE = LogInterceptor.class.getName() + ".startTime";
    private static final String HANDLER_ATTRIBUTE = LogInterceptor.class.getName() + ".handler";

    /** MDC key，写进 logback pattern 的 %X{traceId}，用来把同一请求的多条日志串起来 */
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 虚拟线程下 %thread 不再能唯一标识一次请求，靠 MDC 里的 traceId 串联同一请求的所有日志
        MDC.put(TRACE_ID_MDC_KEY, UUID.randomUUID().toString().replace("-", "").substring(0, 16));

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

        try {
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

            log.debug(
                "HTTP {} {} -> status={} cost={}ms ip={} userId={} handler={}",
                request.getMethod(),
                requestPath,
                response.getStatus(),
                costMs,
                clientIp,
                userId,
                handlerName
            );
        } finally {
            // 虚拟线程用完可能被复用为下一个任务的载体，MDC 不清会串到下一次调用
            MDC.remove(TRACE_ID_MDC_KEY);
        }
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
