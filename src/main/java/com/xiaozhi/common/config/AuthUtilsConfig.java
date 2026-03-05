package com.xiaozhi.common.config;

import com.xiaozhi.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * 认证工具类配置
 *
 * @author Joey
 */
@Slf4j
@Configuration
public class AuthUtilsConfig {

    @Autowired
    private SysUserService userService;

//    @PostConstruct
//    public void init() {
//        AuthUtils.setUserService(userService);
//    }
}
