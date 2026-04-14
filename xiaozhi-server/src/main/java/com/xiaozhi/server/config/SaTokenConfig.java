package com.xiaozhi.server.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置类
 *
 * @author Joey
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Bean
    public StpLogic getStpLogicJwt() {
        return new StpLogicJwtForSimple();
    }

    /**
     * 注册Sa-Token拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册Sa-Token拦截器，拦截所有API请求
        // 不需要登录的接口请使用 @SaIgnore 注解标注
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()) {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                        // CORS 预检请求（OPTIONS）直接放行，不检查登录状态
                        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                            return true;
                        }
                        // 其他请求正常处理
                        return super.preHandle(request, response, handler);
                    }
                }.isAnnotation(true))  // 开启注解鉴权功能，支持 @SaIgnore 等注解
                .addPathPatterns("/api/**");
    }
}
