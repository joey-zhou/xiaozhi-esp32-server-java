package com.xiaozhi;

import com.xiaozhi.communication.ServerAddressProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.extern.slf4j.Slf4j;
@SpringBootApplication
@EnableCaching
@EnableScheduling
@ComponentScan(basePackages = {
    // xiaozhi-common
    "com.xiaozhi.common",
    "com.xiaozhi.communication",
    "com.xiaozhi.utils",
    // xiaozhi-service (全量)
    "com.xiaozhi.agent",
    "com.xiaozhi.authrole",
    "com.xiaozhi.config",
    "com.xiaozhi.device",
    "com.xiaozhi.mcptoolexclude",
    "com.xiaozhi.message",
    "com.xiaozhi.monitoring",
    "com.xiaozhi.operationlog",
    "com.xiaozhi.permission",
    "com.xiaozhi.role",
    "com.xiaozhi.security",
    "com.xiaozhi.service",
    "com.xiaozhi.storage",
    "com.xiaozhi.summary",
    "com.xiaozhi.template",
    "com.xiaozhi.token",
    "com.xiaozhi.user",
    "com.xiaozhi.userauth",
    "com.xiaozhi.task",
    // xiaozhi-ai
    "com.xiaozhi.ai",
    // xiaozhi-server
    "com.xiaozhi.file",
    "com.xiaozhi.mcpserver",
    "com.xiaozhi.memory",
    "com.xiaozhi.music",
    "com.xiaozhi.server",
})
@MapperScan({
    "com.xiaozhi.authrole.dal.mysql.mapper",
    "com.xiaozhi.config.dal.mysql.mapper",
    "com.xiaozhi.device.dal.mysql.mapper",
    "com.xiaozhi.mcptoolexclude.dal.mysql.mapper",
    "com.xiaozhi.message.dal.mysql.mapper",
    "com.xiaozhi.permission.dal.mysql.mapper",
    "com.xiaozhi.role.dal.mysql.mapper",
    "com.xiaozhi.authrolepermission.dal.mysql.mapper",
    "com.xiaozhi.summary.dal.mysql.mapper",
    "com.xiaozhi.template.dal.mysql.mapper",
    "com.xiaozhi.userauth.dal.mysql.mapper",
    "com.xiaozhi.user.dal.mysql.mapper",
    "com.xiaozhi.operationlog.dal.mysql.mapper",
})
@Slf4j
public class XiaozhiApplication {

    @Autowired
    private ServerAddressProvider serverAddressProvider;

    public static void main(String[] args) {
        SpringApplication.run(XiaozhiApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("==========================================================");
        log.info("OTA服务地址: {}", serverAddressProvider.getOtaAddress());
        log.info("==========================================================");
    }
}
