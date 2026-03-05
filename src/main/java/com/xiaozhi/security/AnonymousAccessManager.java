package com.xiaozhi.security;

import com.xiaozhi.security.annotation.AnonymousAccess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.annotation.Resource;
import java.util.HashSet;
import java.util.Set;

/**
 * 匿名访问路径管理器
 * 扫描所有带有@AnonymousAccess 注解的接口，收集白名单路径
 *
 * @author Joey
 */
@Slf4j
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AnonymousAccessManager implements InitializingBean {

    @Resource
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * 匿名访问路径集合
     */
    private static final Set<String> ANONYMOUS_PATHS = new HashSet<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        // 应用启动时扫描所有 Controller，收集带有@AnonymousAccess 注解的路径
        scanAnonymousPaths();
        log.info("匿名访问白名单路径数量：{}", ANONYMOUS_PATHS.size());
        ANONYMOUS_PATHS.forEach(path -> log.debug("匿名路径：{}", path));
    }

    /**
     * 扫描所有带有@AnonymousAccess 注解的路径
     */
    private void scanAnonymousPaths() {
        // 获取所有 RequestMappingInfo
        requestMappingHandlerMapping.getHandlerMethods()
                .forEach((key, value) -> {
                    // 判断方法或类上是否有@AnonymousAccess 注解
                    if (isAnonymousAccess(value)) {
                        // 获取路径模式
                        RequestMappingInfo info = key;
                        if (info.getPathPatternsCondition() != null) {
                            // 将路径模式添加到白名单
                            info.getPathPatternsCondition().getPatternValues()
                                    .forEach(ANONYMOUS_PATHS::add);
                        }
                    }
                });
    }

    /**
     * 判断 HandlerMethod 是否标注了@AnonymousAccess 注解
     */
    private boolean isAnonymousAccess(HandlerMethod handlerMethod) {
        // 检查方法上是否有注解
        boolean hasMethodAnnotation = handlerMethod.hasMethodAnnotation(AnonymousAccess.class);
        // 检查类上是否有注解
        boolean hasClassAnnotation = handlerMethod.getBeanType().isAnnotationPresent(AnonymousAccess.class);
        return hasMethodAnnotation || hasClassAnnotation;
    }

    /**
     * 判断路径是否为匿名访问路径
     * 支持 Ant 路径模式匹配
     */
    public boolean isAnonymousPath(String requestPath) {
        if (requestPath == null || requestPath.isEmpty()) {
            return false;
        }
        
        for (String pattern : ANONYMOUS_PATHS) {
            if (matchesPattern(pattern, requestPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 简单的 Ant 路径模式匹配
     */
    private boolean matchesPattern(String pattern, String path) {
        // 精确匹配
        if (pattern.equals(path)) {
            return true;
        }
        
        // 处理 ** 通配符
        if (pattern.contains("**")) {
            String[] patternParts = pattern.split("\\*\\*");
            if (patternParts.length == 2) {
                String prefix = patternParts[0];
                String suffix = patternParts[1];
                
                // 去除前后缀的空
                prefix = prefix.endsWith("/") ? prefix : prefix + "/";
                suffix = suffix.startsWith("/") ? suffix : "/" + suffix;
                
                if (prefix.equals("/") && path.endsWith(suffix.substring(1))) {
                    return true;
                }
                if (suffix.equals("/") && path.startsWith(prefix.substring(0, prefix.length() - 1))) {
                    return true;
                }
                if (path.startsWith(prefix.substring(0, prefix.length() - 1)) && 
                    path.endsWith(suffix.substring(1))) {
                    return true;
                }
            }
        }
        
        // 处理 * 通配符（单级）
        if (pattern.contains("*") && !pattern.contains("**")) {
            String regex = pattern.replace(".", "\\.").replace("?", ".?").replace("*", "[^/]*");
            return path.matches(regex);
        }
        
        return false;
    }

    /**
     * 获取所有匿名访问路径
     */
    public Set<String> getAnonymousPaths() {
        return new HashSet<>(ANONYMOUS_PATHS);
    }
}
