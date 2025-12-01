package com.xiaozhi.common.config;

import com.xiaozhi.common.interceptor.LogInterceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;  // 改为实现接口
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

import jakarta.annotation.Resource;

import java.io.File;

@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {  // 实现接口而不是继承

    @Resource
    private LogInterceptor logInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
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
            // 获取项目根目录的绝对路径
            String basePath = new File("").getAbsolutePath();

            // 音频文件存储在项目根目录下的audio文件夹中
            String audioPath = "file:" + basePath + File.separator + "audio" + File.separator;

            // 上传文件存储在项目根目录下的uploads文件夹中
            String uploadsPath = "file:" + basePath + File.separator + "uploads" + File.separator;

            // 配置资源映射
            registry.addResourceHandler("/audio/**")
                    .addResourceLocations(audioPath);

            // 为上传文件添加资源映射
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