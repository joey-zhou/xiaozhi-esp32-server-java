package com.xiaozhi.common.config;

import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ThreadConfig {

//    @Bean
//    public AsyncTaskExecutor applicationTaskExecutor() {
//        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
//    }
    @Bean
    public ThreadPoolTaskExecutorCustomizer threadPoolTaskExecutorCustomizer() {
        return executor -> {
            executor.setThreadNamePrefix("task-");

        };
    }
//    @Bean
//    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
//        return protocolHandler -> {
//            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
//        };
//    }
}