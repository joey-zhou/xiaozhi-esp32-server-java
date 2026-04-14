package com.xiaozhi.server.config;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.server.web.LogInterceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

import jakarta.annotation.Resource;

import java.io.File;

@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private LogInterceptor logInterceptor;

    @Resource
    private RateLimitInterceptor rateLimitInterceptor;

    @Resource
    private RuntimePathConfig runtimePathConfig;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(logInterceptor)
                .addPathPatterns("/api/**")
                .order(100);

        // 登录、注册、验证码等端点限流
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(
                        "/api/user/login",
                        "/api/user/tel-login",
                        "/api/user/wx-login",
                        "/api/user",                    // 注册 POST
                        "/api/user/resetPassword",
                        "/api/user/sendEmailCaptcha",
                        "/api/user/sendSmsCaptcha"
                )
                .order(10);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            String audioPath = runtimePathConfig.resolveAudioDir().toUri().toString();
            String uploadsPath = new File("uploads").getAbsoluteFile().toURI().toString();

            registry.addResourceHandler("/audio/**")
                    .addResourceLocations(audioPath);

            registry.addResourceHandler("/uploads/**")
                    .addResourceLocations(uploadsPath);

        } catch (Exception e) {
            log.error("添加资源失败", e);
        }
    }

    /**
     * 配置路径匹配参数
     */
    @Override
    @SuppressWarnings("deprecation") // 暂时抑制过时警告
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 使用推荐的方法设置尾部斜杠匹配
        configurer.setUseTrailingSlashMatch(true);
    }

    /**
     * 配置异步请求支持
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 设置异步请求超时时间为120秒，比SSE的60秒超时更长
        configurer.setDefaultTimeout(120000L);
    }
}
