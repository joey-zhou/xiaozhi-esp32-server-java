package com.xiaozhi.common.config;

import com.xiaozhi.service.SysUserService;
import com.xiaozhi.utils.AuthUtils;
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
    private SysUserService userService;

    @PostConstruct
    public void init() {
        AuthUtils.setUserService(userService);
    }
}
