package com.xiaozhi.server.config;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.file.LocalFileAccessInterceptor;
import com.xiaozhi.server.web.LogInterceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;


@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private LogInterceptor logInterceptor;

    @Resource
    private RateLimitInterceptor rateLimitInterceptor;

    @Resource
    private RuntimePathConfig runtimePathConfig;

    @Resource
    private LocalFileAccessInterceptor localFileAccessInterceptor;

    /** 与 LocalStorageService、LocalFileUrlPolicy 同一配置项，三处必须指向同一个目录 */
    @Value("${xiaozhi.upload-path:uploads}")
    private String uploadPath;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(logInterceptor)
                .addPathPatterns("/api/**")
                .order(100);

        // 登录、注册、验证码、设备绑定、OTA 等端点限流
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(
                        "/api/user/login",
                        "/api/user/tel-login",
                        "/api/user/wx-login",
                        "/api/user",                    // 注册 POST
                        "/api/user/resetPassword",
                        "/api/user/sendEmailCaptcha",
                        "/api/user/sendSmsCaptcha",
                        "/api/user/checkUser",          // 匿名可调，不限速就是账号存在性预言机
                        "/api/config/test",             // 每次试拨都是一次计费外呼
                        "/api/device",                  // 验证码绑定 POST
                        "/api/device/scan-bind",
                        "/api/device/ota",
                        "/api/device/ota/activate"
                )
                .order(10);

        // 录音与上传目录以静态资源暴露，路径基本可枚举，必须校验时效签名
        registry.addInterceptor(localFileAccessInterceptor)
                .addPathPatterns("/audio/**", "/uploads/**")
                .order(20);
    }

    /**
     * 账号维度限流依赖请求体里的用户名/手机号/邮箱，必须在 DispatcherServlet 之前解析并缓存请求体
     */
    @Bean
    public FilterRegistrationBean<RateLimitInterceptor.AccountSubjectFilter> accountSubjectFilter() {
        FilterRegistrationBean<RateLimitInterceptor.AccountSubjectFilter> registration =
                new FilterRegistrationBean<>(new RateLimitInterceptor.AccountSubjectFilter());
        registration.addUrlPatterns("/api/user", "/api/user/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
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
            String uploadsPath = runtimePathConfig.resolveStorageKey(uploadPath).toUri().toString();

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
