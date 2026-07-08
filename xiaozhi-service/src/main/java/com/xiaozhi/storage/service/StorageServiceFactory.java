package com.xiaozhi.storage.service;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.storage.service.impl.AliyunOssStorageService;
import com.xiaozhi.storage.service.impl.LocalStorageService;
import com.xiaozhi.storage.service.impl.S3StorageService;
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
 * 云端客户端会被缓存复用（COS/OSS SDK 客户端均线程安全）。缓存标识由 provider + configId + updateTime
 * 组成，因此切换 provider、切换默认配置、或修改当前配置的任意字段（ak/sk/endpoint/bucket 等）都会触发重建。
 */
@Slf4j
@Component
public class StorageServiceFactory {

    @Resource
    private ConfigService configService;

    @Resource
    private LocalStorageService localStorageService;

    private volatile StorageService cachedCloudService;
    private volatile String cachedSignature;

    /**
     * 获取当前生效的存储服务
     */
    public StorageService getStorageService() {
        try {
            ConfigBO ossConfig = getDefaultOssConfig();

            if (ossConfig == null || "local".equals(ossConfig.getProvider())) {
                return localStorageService;
            }

            // 缓存标识包含 configId 与 updateTime：同 provider 下改动 ak/sk/endpoint/bucket 等字段也能触发重建
            String signature = ossConfig.getProvider() + ":" + ossConfig.getConfigId() + ":" + ossConfig.getUpdateTime();
            if (signature.equals(cachedSignature) && cachedCloudService != null) {
                return cachedCloudService;
            }

            synchronized (this) {
                if (signature.equals(cachedSignature) && cachedCloudService != null) {
                    return cachedCloudService;
                }
                shutdownCached();
                cachedCloudService = createStorageService(ossConfig);
                cachedSignature = signature;
                log.info("存储服务已切换到: {} (configId={})", ossConfig.getProvider(), ossConfig.getConfigId());
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
            // S3 兼容存储：前端按厂商分列，底层统一走 S3StorageService（仅 endpoint 不同）
            case "s3", "minio", "r2", "b2", "huawei-obs", "wasabi", "do-spaces", "qiniu" ->
                    new S3StorageService(config);
            default -> {
                log.warn("未知的存储 provider: {}，使用本地存储", config.getProvider());
                yield localStorageService;
            }
        };
    }

    private ConfigBO getDefaultOssConfig() {
        return configService.getDefaultBO("oss");
    }

    /**
     * 清除进程内缓存的云存储客户端。
     * <p>
     * 供配置变更广播（{@code RedisSubscriber.onConfigChanged}）调用：OSS 默认配置切换后，
     * 各实例需强制丢弃旧的缓存客户端，下次 {@link #getStorageService()} 重新按最新配置构建，
     * 避免 dialogue 等独立进程继续使用切换前的存储服务。
     */
    public synchronized void refresh() {
        // 先清除 default:oss 的 Redis 缓存，避免下面重建时又命中其它实例回填的旧默认配置
        configService.evictDefaultCache("oss");
        shutdownCached();
        cachedCloudService = null;
        cachedSignature = null;
        log.info("存储服务缓存已清除，将按最新配置重建");
    }

    private void shutdownCached() {
        if (cachedCloudService instanceof TencentCosStorageService cos) {
            cos.shutdown();
        } else if (cachedCloudService instanceof AliyunOssStorageService oss) {
            oss.shutdown();
        } else if (cachedCloudService instanceof S3StorageService s3) {
            s3.shutdown();
        }
    }

    @PreDestroy
    public void destroy() {
        shutdownCached();
    }
}
