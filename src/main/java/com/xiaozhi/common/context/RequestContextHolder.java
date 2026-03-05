package com.xiaozhi.common.context;

/**
 * 请求上下文持有器
 * 用于存储当前 HTTP 请求的信息，便于在 SQL 报错时追踪
 *
 * @author Joey
 */
public class RequestContextHolder {

    private static final ThreadLocal<String> REQUEST_URI = new ThreadLocal<>();
    private static final ThreadLocal<String> METHOD = new ThreadLocal<>();
    private static final ThreadLocal<String> API_PATH = new ThreadLocal<>();

    /**
     * 设置请求信息
     */
    public static void setRequestInfo(String requestUri, String method, String apiPath) {
        REQUEST_URI.set(requestUri);
        METHOD.set(method);
        API_PATH.set(apiPath);
    }

    /**
     * 获取当前请求 URI
     */
    public static String getRequestUri() {
        return REQUEST_URI.get();
    }

    /**
     * 获取当前请求方法
     */
    public static String getMethod() {
        return METHOD.get();
    }

    /**
     * 获取当前 API 路径
     */
    public static String getApiPath() {
        return API_PATH.get();
    }

    /**
     * 获取完整的请求信息
     */
    public static String getFullRequestInfo() {
        String method = METHOD.get();
        String uri = REQUEST_URI.get();
        if (method != null && uri != null) {
            return method + " " + uri;
        }
        return "未知请求";
    }

    /**
     * 清除请求信息
     */
    public static void clear() {
        REQUEST_URI.remove();
        METHOD.remove();
        API_PATH.remove();
    }
}
