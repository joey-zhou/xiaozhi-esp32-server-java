package com.xiaozhi.common.interceptor;

import com.xiaozhi.utils.AuthUtils;
import com.xiaozhi.utils.LogUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Web 请求拦截器
 * 记录请求信息到上下文，便于 SQL 报错时追踪
 *
 * @author Joey
 */
@Component
public class RequestInterceptor implements HandlerInterceptor {

    private static final Logger log = LogUtils.getLogger(RequestInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 记录请求信息
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        String queryString = request.getQueryString();

        // 构建完整的 API 路径
        String apiPath = requestUri;
        if (queryString != null && !queryString.isEmpty()) {
            apiPath += "?" + queryString;
        }


        // 存储到上下文
        AuthUtils.setRequestInfo(requestUri, method, apiPath);

        log.debug("【请求开始】{} {}", method, requestUri);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求完成后清除上下文
        AuthUtils.clear();

        log.debug("【请求结束】{} {} - Status: {}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus());
    }
}
