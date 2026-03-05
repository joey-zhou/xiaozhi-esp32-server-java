package com.xiaozhi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching(proxyTargetClass = true)
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true,exposeProxy = true)
public class XiaozhiApplication {

    Logger logger = LoggerFactory.getLogger(XiaozhiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(XiaozhiApplication.class, args);
    }
}