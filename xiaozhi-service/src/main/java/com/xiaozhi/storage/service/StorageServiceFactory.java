package com.xiaozhi.storage.service;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.storage.service.impl.AliyunOssStorageService;
import com.xiaozhi.storage.service.impl.LocalStorageService;
import com.xiaozhi.storage.service.impl.TencentCosStorageService;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
/**
 * 存储服务工厂。
 * 从 sys_config（configType="oss"）读取默认 OSS 配置，按 provider 创建对应实现。
 * 无配置或配置无效时 fallback 到本地存储。
 * <p>
 * 云端客户端会被缓存复用（COS/OSS SDK 客户端均线程安全），当 provider 配置变更时自动重建。
 */
@Slf4j
@Component
public class StorageServiceFactory {

    @Resource
    private ConfigService configService;

    @Resource
    private LocalStorageService localStorageService;

    private volatile StorageService cachedCloudService;
    private volatile String cachedProvider;

    /**
     * 获取当前生效的存储服务
     */
    public StorageService getStorageService() {
        try {
            ConfigBO ossConfig = getDefaultOssConfig();

            if (ossConfig == null || "local".equals(ossConfig.getProvider())) {
                return localStorageService;
            }

            String provider = ossConfig.getProvider();
            if (provider.equals(cachedProvider) && cachedCloudService != null) {
                return cachedCloudService;
            }

            synchronized (this) {
                if (provider.equals(cachedProvider) && cachedCloudService != null) {
                    return cachedCloudService;
                }
                shutdownCached();
                cachedCloudService = createStorageService(ossConfig);
                cachedProvider = provider;
                log.info("存储服务已切换到: {}", provider);
                return cachedCloudService;
            }
        } catch (Exception e) {
            log.warn("获取 OSS 配置失败，使用本地存储: {}", e.getMessage());
            return localStorageService;
        }
    }

    /**
     * 根据配置创建对应的存储服务
     */
    public StorageService createStorageService(ConfigBO config) {
        return switch (config.getProvider()) {
            case "tencent" -> new TencentCosStorageService(config);
            case "aliyun" -> new AliyunOssStorageService(config);
            default -> {
                log.warn("未知的存储 provider: {}，使用本地存储", config.getProvider());
                yield localStorageService;
            }
        };
    }

    private ConfigBO getDefaultOssConfig() {
        return configService.getDefaultBO("oss");
    }

    private void shutdownCached() {
        if (cachedCloudService instanceof TencentCosStorageService cos) {
            cos.shutdown();
        } else if (cachedCloudService instanceof AliyunOssStorageService oss) {
            oss.shutdown();
        }
    }

    @PreDestroy
    public void destroy() {
        shutdownCached();
    }
}
