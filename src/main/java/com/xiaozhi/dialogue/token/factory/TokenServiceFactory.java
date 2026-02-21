package com.xiaozhi.dialogue.token.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.xiaozhi.dialogue.token.TokenService;
import com.xiaozhi.dialogue.token.providers.AliyunTokenService;
import com.xiaozhi.dialogue.token.providers.CozeTokenService;
import com.xiaozhi.entity.SysConfig;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class TokenServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(TokenServiceFactory.class);

    // 缓存已初始化的服务：键为"provider:configId"格式
    private final Map<String, TokenService> serviceCache = new ConcurrentHashMap<>();
    
    // 使用虚拟线程的定时任务执行器
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        // 使用虚拟线程工厂创建定时任务执行器
        scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual()
            .name("token-cleanup-scheduler-", 0)
            .factory());
        
        // 启动定时清理任务，每小时执行一次
        scheduler.scheduleAtFixedRate(this::cleanupUnusedTokens, 1, 1, TimeUnit.HOURS);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // 创建缓存键，包含provider、configId
    private String createCacheKey(SysConfig config, String provider) {
        Integer configId = config != null && config.getConfigId() != null ? 
                          config.getConfigId() : -1;
        return provider + ":" + configId;
    }

    /**
     * 根据配置获取Token服务
     */
    public TokenService getTokenService(SysConfig config) {
        var provider = config.getProvider();
        var cacheKey = createCacheKey(config, provider);

        // 使用 computeIfAbsent 确保原子性操作，避免并发创建多个实例
        return serviceCache.computeIfAbsent(cacheKey, k -> createTokenService(config));
    }

    /**
     * 根据配置创建Token服务
     */
    private TokenService createTokenService(SysConfig config) {
        return switch (config.getProvider()) {
            case "aliyun", "aliyun-nls" -> new AliyunTokenService(config);
            case "coze" -> new CozeTokenService(config);
            default -> throw new IllegalArgumentException("不支持的Token服务提供商: " + config.getProvider());
        };
    }

    /**
     * 移除指定配置的缓存
     */
    public void removeCache(SysConfig config) {
        String provider = config.getProvider();
        Integer configId = config.getConfigId();
        String cacheKey = provider + ":" + configId;
        
        TokenService service = serviceCache.remove(cacheKey);
        if (service != null) {
            service.clearTokenCache();
        }
    }

    /**
     * 清理需要清除缓存的Token（超过24小时未使用）
     */
    private void cleanupUnusedTokens() {
        
        // 使用虚拟线程并行处理清理任务
        serviceCache.entrySet().parallelStream().forEach(entry -> {
            Thread.startVirtualThread(() -> {
                try {
                    TokenService service = entry.getValue();
                    
                    // 检查是否为阿里云服务且需要清除缓存
                    if (service instanceof AliyunTokenService aliyunService) {
                        if (aliyunService.needsCacheCleanup()) {
                            service.clearTokenCache();
                            serviceCache.remove(entry.getKey());
                        }
                    }
                } catch (Exception e) {
                    logger.error("清理Token缓存时发生错误: {}", e.getMessage(), e);
                }
            });
        });
    }

    /**
     * 异步刷新所有即将过期的Token
     */
    public void refreshExpiringTokensAsync() {
        
        serviceCache.values().parallelStream().forEach(service -> {
            Thread.startVirtualThread(() -> {
                try {
                    if (service instanceof AliyunTokenService aliyunService) {
                        // 检查是否需要刷新
                        aliyunService.getToken(); // 内部会处理刷新逻辑
                    }
                } catch (Exception e) {
                    logger.error("刷新Token时发生错误: {}", e.getMessage(), e);
                }
            });
        });
    }

    /**
     * 获取当前缓存的服务数量
     */
    public int getCacheSize() {
        return serviceCache.size();
    }

    /**
     * 清理所有缓存
     */
    public void clearAllCache() {
        // 使用虚拟线程并行清理
        serviceCache.values().parallelStream().forEach(service -> {
            Thread.startVirtualThread(() -> {
                try {
                    service.clearTokenCache();
                } catch (Exception e) {
                    logger.error("清理Token缓存时发生错误: {}", e.getMessage(), e);
                }
            });
        });
        serviceCache.clear();
    }
}
