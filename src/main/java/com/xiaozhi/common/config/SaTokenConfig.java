package com.xiaozhi.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysUserService;
import com.xiaozhi.utils.CmsUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置类
 *
 * @author Joey
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Resource
    private SysUserService userService;

    /**
     * 注册Sa-Token拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册Sa-Token拦截器，拦截所有API请求
        // 不需要登录的接口请使用 @SaIgnore 注解标注
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**");

        // 注册用户信息设置拦截器，在sa-token之后执行
        registry.addInterceptor(new UserSetupInterceptor(userService))
                .addPathPatterns("/api/**")
                .order(1); // 设置较高的优先级，确保在sa-token之后执行
    }

    /**
     * 用户信息设置拦截器
     * 从sa-token获取登录用户ID，查询用户信息并设置到Request属性中
     */
    private static class UserSetupInterceptor implements HandlerInterceptor {
        private final SysUserService userService;

        public UserSetupInterceptor(SysUserService userService) {
            this.userService = userService;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            // 检查是否已登录
            if (StpUtil.isLogin()) {
                try {
                    // 从sa-token获取登录用户ID
                    Object loginId = StpUtil.getLoginId();
                    if (loginId != null) {
                        Integer userId = Integer.valueOf(loginId.toString());
                        // 查询用户信息
                        SysUser user = userService.selectUserByUserId(userId);
                        if (user != null) {
                            // 设置用户信息到Request属性
                            CmsUtils.setUser(request, user);
                        }
                    }
                } catch (Exception e) {
                    // 忽略异常，继续处理请求
                }
            }
            return true;
        }
    }
}
