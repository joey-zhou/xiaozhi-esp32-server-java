package com.xiaozhi.service.config;

import com.xiaozhi.utils.AuthUtils;
import com.xiaozhi.user.service.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;

/**
 * 认证工具类配置
 *
 * @author Joey
 */
@Configuration
public class AuthUtilsConfig {

    @Resource
    private UserService userService;

    @PostConstruct
    public void init() {
        AuthUtils.setUserService(userService);
    }
}
