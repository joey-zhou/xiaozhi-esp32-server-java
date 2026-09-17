package com.xiaozhi.storage.service;

import com.xiaozhi.common.exception.OperationFailedException;
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
 * 无配置或配置显式为 local 时才 fallback 到本地存储；读取/初始化中途出错一律抛异常，
 * 不能悄悄退回本地存储——否则写入的文件只落在当次实例磁盘上，其它实例按 OSS 路径读会 404。
 * <p>
 * 云端客户端会被缓存复用（COS/OSS SDK 客户端均线程安全）。缓存标识由 provider + configId + updateTime
 * 组成，因此切换 provider、切换默认配置、或修改当前配置的任意字段（ak/sk/endpoint/bucket 等）都会触发重建。
 * <p>
 * <b>写入用 {@link #getStorageService()}（当前生效的实现），读取用本类的 accessUrlOf / downloadFrom /
 * removeFrom（按值本身的形态选实现）。</b>
 * 库里存的历史值有两种形态：相对路径是本地文件，{@code http(s)://} 开头是云上对象。
 * 换存储实现不会、也不该改写这些历史值，所以读取时只能认值的形态——
 * 拿当前生效的实现去解析所有历史值，会让切到对象存储后的历史本地录音全部 403（云端认不出相对路径便原样返回，
 * 丢掉本地访问所需的签名），也会让切回本地后的云地址无从解析。
 */
@Slf4j
@Component
public class StorageServiceFactory {

    // 默认 OSS 配置本身极少变化，进程内短暂缓存吸收一轮对话里的多次重复读取；
    // 配置变更广播会调用 refresh() 立即失效，不依赖这个 TTL 过期
    private static final long OSS_CONFIG_CACHE_MILLIS = 30_000L;

    @Resource
    private ConfigService configService;

    @Resource
    private LocalStorageService localStorageService;

    private volatile StorageService cachedCloudService;
    private volatile String cachedSignature;

    private volatile ConfigBO cachedOssConfig;
    private volatile long cachedOssConfigAt;

    /**
     * 获取当前生效的存储服务
     */
    public StorageService getStorageService() {
        ConfigBO ossConfig;
        try {
            ossConfig = getDefaultOssConfig();
        } catch (Exception e) {
            throw new OperationFailedException("存储配置读取失败，请稍后重试", e);
        }

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
            try {
                shutdownCached();
                cachedCloudService = createStorageService(ossConfig);
                cachedSignature = signature;
                log.info("存储服务已切换到: {} (configId={})", ossConfig.getProvider(), ossConfig.getConfigId());
                return cachedCloudService;
            } catch (Exception e) {
                throw new OperationFailedException("存储服务初始化失败，请稍后重试", e);
            }
        }
    }

    /**
     * 根据配置创建对应的存储服务
     */
    public StorageService createStorageService(ConfigBO config) {
        StorageService service = switch (config.getProvider()) {
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
        return service;
    }

    /**
     * 按值的形态选出能解析它的实现：相对路径归本地，{@code http(s)://} 归当前云实现。
     * <p>
     * 当前生效的是本地存储、而值是云地址时，返回本地实现——它对认不出的值原样返回，
     * 好过拿本地策略去套一个云地址。这种情况说明存储被从对象存储切回了本地，历史云对象已不可达。
     */
    public StorageService resolveFor(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return localStorageService;
        }
        if (storedPath.startsWith("http://") || storedPath.startsWith("https://")) {
            StorageService current = getStorageService();
            return current.getProvider().equals(localStorageService.getProvider()) ? localStorageService : current;
        }
        return localStorageService;
    }

    /** 历史路径转可访问 URL，实现按值的形态选，不按当前生效配置选 */
    public String accessUrlOf(String storedPath) {
        return resolveFor(storedPath).getAccessUrl(storedPath);
    }

    /** 读历史文件内容，实现按值的形态选 */
    public byte[] downloadFrom(String storedPath) {
        return resolveFor(storedPath).download(storedPath);
    }

    /** 删历史文件，实现按值的形态选；删错地方等于删不掉，文件会一直留着 */
    public void removeFrom(String storedPath) {
        resolveFor(storedPath).remove(storedPath);
    }

    private ConfigBO getDefaultOssConfig() {
        ConfigBO cached = cachedOssConfig;
        if (cached != null && System.currentTimeMillis() - cachedOssConfigAt < OSS_CONFIG_CACHE_MILLIS) {
            return cached;
        }
        ConfigBO result = configService.getDefaultBO("oss");
        cachedOssConfig = result;
        cachedOssConfigAt = System.currentTimeMillis();
        return result;
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
        cachedOssConfig = null;
        cachedOssConfigAt = 0;
        shutdownCached();
        cachedCloudService = null;
        cachedSignature = null;
        log.info("存储服务缓存已清除，将按最新配置重建");
    }

    /**
     * 释放缓存实例持有的 SDK 客户端。
     * 具体释放什么由实现自己在 {@link StorageService#shutdown()} 里决定，
     * 本地存储没有常驻资源，默认实现是空的。
     */
    private void shutdownCached() {
        if (cachedCloudService != null) {
            cachedCloudService.shutdown();
        }
    }

    @PreDestroy
    public void destroy() {
        shutdownCached();
    }
}
