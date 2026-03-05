package com.xiaozhi.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 性能优化配置类
 * 配置各种线程池和其他性能相关组件
 */
@Configuration
public class PerformanceConfig {

    /**
     * 配置异步任务执行器
     */
    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：CPU核心数
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        // 最大线程数：CPU核心数的2倍
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 2);
        // 队列容量
        executor.setQueueCapacity(200);
        // 线程名称前缀
        executor.setThreadNamePrefix("perf-task-");
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 初始化
        executor.initialize();
        return executor;
    }

    /**
     * 配置TTS专用线程池
     */
    @Bean("ttsTaskExecutor")
    public ThreadPoolTaskExecutor ttsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 专门处理TTS任务的核心线程数
        executor.setCorePoolSize(Math.max(4, Runtime.getRuntime().availableProcessors() / 2));
        // 最大线程数
        executor.setMaxPoolSize(Math.max(8, Runtime.getRuntime().availableProcessors()));
        // 队列容量
        executor.setQueueCapacity(100);
        // 线程名称前缀
        executor.setThreadNamePrefix("tts-task-");
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 初始化
        executor.initialize();
        return executor;
    }

    /**
     * 配置音频处理专用线程池
     */
    @Bean("audioProcessingExecutor")
    public ThreadPoolTaskExecutor audioProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 专门处理音频处理的核心线程数
        executor.setCorePoolSize(Math.max(2, Runtime.getRuntime().availableProcessors() / 4));
        // 最大线程数
        executor.setMaxPoolSize(Math.max(4, Runtime.getRuntime().availableProcessors() / 2));
        // 队列容量
        executor.setQueueCapacity(50);
        // 线程名称前缀
        executor.setThreadNamePrefix("audio-processing-");
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 初始化
        executor.initialize();
        return executor;
    }
}