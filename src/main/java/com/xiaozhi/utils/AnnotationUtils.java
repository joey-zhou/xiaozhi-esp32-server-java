package com.xiaozhi.utils;

import com.xiaozhi.common.annation.SaIgnore;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注解工具类
 * 用于检测请求是否标注了特定注解
 *
 * @author Joey
 */
@Component
public class AnnotationUtils {

    private static final Logger log = LoggerFactory.getLogger(AnnotationUtils.class);

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * 缓存所有标注了 @SaIgnore 的接口路径
     * key: HTTP 方法 + 路径 (例如："GET /api/user/login")
     */
    private static final Set<String> SA_IGNORE_PATHS = ConcurrentHashMap.newKeySet();

    /**
     * 标记是否已初始化
     */
    private static volatile boolean initialized = false;

    /**
     * 初始化：扫描所有标注了 @SaIgnore 的接口
     * 应该在应用启动时调用
     */
    public void initSaIgnorePaths() {
        if (initialized) {
            return;
        }

        synchronized (AnnotationUtils.class) {
            if (initialized) {
                return;
            }

            SA_IGNORE_PATHS.clear();
            scanSaIgnoreAnnotations();
            initialized = true;

            log.info("初始化完成，共扫描到 {} 个 @SaIgnore 注解的接口", SA_IGNORE_PATHS.size());
            for (String path : SA_IGNORE_PATHS) {
                log.debug("  - {}", path);
            }
        }
    }

    /**
     * 扫描所有标注了 @SaIgnore 的接口
     */
    private void scanSaIgnoreAnnotations() {
        if (requestMappingHandlerMapping == null) {
            log.warn("RequestMappingHandlerMapping 未注入，无法扫描 @SaIgnore 注解");
            return;
        }

        // 获取所有请求映射信息
        for (RequestMappingInfo mappingInfo : requestMappingHandlerMapping.getHandlerMethods().keySet()) {
            HandlerMethod handlerMethod = requestMappingHandlerMapping.getHandlerMethods().get(mappingInfo);

            // 检查类上是否有 @SaIgnore 注解
            boolean classHasSaIgnore = handlerMethod.getBeanType().isAnnotationPresent(SaIgnore.class);

            // 检查方法上是否有 @SaIgnore 注解
            Method method = handlerMethod.getMethod();
            boolean methodHasSaIgnore = method.isAnnotationPresent(SaIgnore.class);

            // 如果类或方法上有 @SaIgnore 注解，则记录所有映射路径
            if (classHasSaIgnore || methodHasSaIgnore) {
                // 获取所有路径模式
                Set<String> patterns = mappingInfo.getPathPatternsCondition() != null
                        ? mappingInfo.getPathPatternsCondition().getPatternValues()
                        : Set.of();

                // 获取所有 HTTP 方法（需要转换为 String）
                Set<String> httpMethods = new HashSet<>();
                if (mappingInfo.getMethodsCondition() != null) {
                    for (org.springframework.web.bind.annotation.RequestMethod requestMethod : 
                            mappingInfo.getMethodsCondition().getMethods()) {
                        httpMethods.add(requestMethod.name());
                    }
                }

                // 如果没有指定 HTTP 方法，默认为所有方法
                if (httpMethods.isEmpty()) {
                    httpMethods = Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
                }

                // 如果没有指定路径，使用默认路径
                if (patterns.isEmpty()) {
                    patterns = Set.of("/**");
                }

                // 将所有组合添加到缓存
                for (String pattern : patterns) {
                    // 移除通配符，存储具体路径
                    String cleanPattern = pattern.replace("**", "*");
                    for (String httpMethod : httpMethods) {
                        String key = httpMethod + " " + cleanPattern;
                        SA_IGNORE_PATHS.add(key);
                    }
                }

                String annotationSource = classHasSaIgnore ? "类" : "方法";
                log.debug("发现 @SaIgnore 注解：{} - {}.{}()",
                        annotationSource,
                        handlerMethod.getBeanType().getSimpleName(),
                        method.getName());
            }
        }
    }

    /**
     * 检查请求对应的方法是否标注了 @SaIgnore
     *
     * @param request 请求对象
     * @return 如果标注了 @SaIgnore 返回 true
     */
    public boolean hasSaIgnoreAnnotation(HttpServletRequest request) {
        // 确保已初始化
        if (!initialized) {
            initSaIgnorePaths();
        }

        String method = request.getMethod();
        String path = request.getRequestURI();

        // 检查是否在缓存的路径中
        for (String cachedPath : SA_IGNORE_PATHS) {
            String[] parts = cachedPath.split(" ", 2);
            if (parts.length != 2) {
                continue;
            }

            String cachedMethod = parts[0];
            String cachedPattern = parts[1];

            // 方法匹配
            if (!method.equals(cachedMethod)) {
                continue;
            }

            // 路径匹配（支持通配符）
            if (pathMatches(cachedPattern, path)) {
                return true;
            }
        }

        // 如果缓存中没有找到，尝试动态检测（备用方案）
        return hasSaIgnoreAnnotationDynamic(request);
    }

    /**
     * 动态检测请求是否标注了 @SaIgnore（备用方案）
     */
    private boolean hasSaIgnoreAnnotationDynamic(HttpServletRequest request) {
        try {
            HandlerExecutionChain handlerChain = requestMappingHandlerMapping.getHandler(request);
            if (handlerChain != null) {
                Object handler = handlerChain.getHandler();
                if (handler instanceof HandlerMethod) {
                    HandlerMethod handlerMethod = (HandlerMethod) handler;
                    Method method = handlerMethod.getMethod();

                    // 检查方法上是否有 @SaIgnore 注解
                    if (method.isAnnotationPresent(SaIgnore.class)) {
                        return true;
                    }

                    // 检查类上是否有 @SaIgnore 注解
                    if (handlerMethod.getBeanType().isAnnotationPresent(SaIgnore.class)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // 忽略异常
            log.debug("动态检测 @SaIgnore 注解失败：{}", e.getMessage());
        }
        return false;
    }

    /**
     * 路径匹配（支持通配符）
     */
    private boolean pathMatches(String pattern, String path) {
        if (pattern.equals(path)) {
            return true;
        }

        if (pattern.equals("/*") || pattern.equals("/**")) {
            return true;
        }

        // 处理 /** 通配符
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }

        // 处理 /* 通配符
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return path.startsWith(prefix + "/");
        }

        // 处理中间通配符
        if (pattern.contains("/**")) {
            String[] parts = pattern.split("\\/\\*\\*");
            if (parts.length == 2) {
                return path.startsWith(parts[0]) && path.endsWith(parts[1]);
            }
        }

        return false;
    }

    /**
     * 获取所有标注了 @SaIgnore 的接口路径列表
     *
     * @return 接口路径列表（格式："HTTP 方法 路径"）
     */
    public List<String> getSaIgnorePaths() {
        if (!initialized) {
            initSaIgnorePaths();
        }
        return new ArrayList<>(SA_IGNORE_PATHS);
    }

    /**
     * 检查方法是否标注了 @SaIgnore
     *
     * @param method 方法对象
     * @return 如果标注了返回 true
     */
    public static boolean hasSaIgnoreAnnotation(Method method) {
        return method != null && method.isAnnotationPresent(SaIgnore.class);
    }

    /**
     * 检查类是否标注了 @SaIgnore
     *
     * @param clazz 类对象
     * @return 如果标注了返回 true
     */
    public static boolean hasSaIgnoreAnnotation(Class<?> clazz) {
        return clazz != null && clazz.isAnnotationPresent(SaIgnore.class);
    }

    /**
     * 清除缓存（用于测试或重新加载）
     */
    public void clearCache() {
        SA_IGNORE_PATHS.clear();
        initialized = false;
        log.info("已清除 @SaIgnore 注解缓存");
    }
}
