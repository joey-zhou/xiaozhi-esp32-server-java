package com.xiaozhi.ai.stt;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.ai.stt.providers.*;
import com.xiaozhi.common.port.TokenResolver;
import com.xiaozhi.common.model.bo.ConfigBO;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SttServiceFactory {

    @Resource
    private TokenResolver tokenResolver;

    @Resource
    private RuntimePathConfig runtimePathConfig;

    // 缓存已初始化的服务：key format: "provider:configId"
    private final Map<String, SttService> serviceCache = new ConcurrentHashMap<>();

    // 默认服务提供商名称
    private static final String DEFAULT_PROVIDER = "vosk";

    // 标记Vosk是否初始化成功
    private boolean voskInitialized = false;

    // 备选默认提供商（当Vosk初始化失败时使用）
    private String fallbackProvider = null;

    /**
     * 应用启动时自动初始化Vosk服务
     */
    @PostConstruct
    public void initializeDefaultSttService() {
        log.info("正在初始化默认语音识别服务(Vosk)...");
        initializeVosk();
        if (voskInitialized) {
            log.info("默认语音识别服务(Vosk)初始化成功，可直接使用");
        } else {
            log.warn("默认语音识别服务(Vosk)初始化失败，将在需要时尝试使用备选服务");
        }
    }

    /**
     * 初始化Vosk服务
     */
    private synchronized SttService initializeVosk() {
        if (serviceCache.containsKey(DEFAULT_PROVIDER)) {
            return serviceCache.get(DEFAULT_PROVIDER);
        }

        try {
            var voskService = new VoskSttService(
                    runtimePathConfig.resolveNativeLibDir().toString(),
                    runtimePathConfig.resolveVoskModelDir().toString()
            );
            voskService.initialize();
            
            // 检查模型是否真正加载成功
            if (voskService instanceof VoskSttService && !((VoskSttService)voskService).isModelLoaded()) {
                throw new Exception("Vosk model was not properly loaded");
            }
            
            serviceCache.put(DEFAULT_PROVIDER, voskService);
            voskInitialized = true;
            log.info("Vosk STT服务初始化成功");
            return voskService;
        } catch (Throwable e) {
            voskInitialized = false;
            log.warn("Vosk STT服务初始化失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取默认STT服务
     */
    public SttService getDefaultSttService() {
        return getSttService(null);
    }

    /**
     * 根据配置获取STT服务
     */
    public SttService getSttService(ConfigBO config) {
        if (config == null) {
            config = new ConfigBO().setProvider(DEFAULT_PROVIDER).setConfigId(-1);
        }

        // 对于API服务，使用"provider:configId"作为缓存键，确保每个配置使用独立的服务实例
        var cacheKey = config.getProvider() + ":" + config.getConfigId();

        // 检查是否已有该配置的服务实例
        if (serviceCache.containsKey(cacheKey)) {
            return serviceCache.get(cacheKey);
        }

        // 创建新的API服务实例
        var service = createApiService(config);
        serviceCache.put(cacheKey, service);

        // 如果没有备选默认服务，将此服务设为备选
        if (fallbackProvider == null) {
            fallbackProvider = cacheKey;
        }

        return service;
    }

    /**
     * 按配置新建一个一次性的STT服务，既不读缓存也不写缓存，用完即弃。
     * <p>
     * 硬约束：仅供未保存的临时配置（如配置测试）使用。这类配置的 configId 不指向真实配置，
     * 走 {@link #getSttService(ConfigBO)} 会把临时凭据留在缓存里，被后续真实会话取到。
     * 本地 vosk 无凭据，仍返回共享实例。
     */
    public SttService createTransientSttService(@Nonnull ConfigBO config) {
        return createApiService(config);
    }

    /**
     * 根据配置创建API类型的STT服务
     */
    private SttService createApiService(@Nonnull ConfigBO config) {
        return switch (config.getProvider()) {
            case "tencent" -> new TencentSttService(config);
            case "aliyun" -> new AliyunSttService(config);
            case "aliyun-nls" -> {
                // 为NLS创建阿里云Token服务
                yield new AliyunNlsSttService(config, tokenResolver);
            }
            case "funasr" -> new FunASRSttService(config);
            case "xfyun" -> new XfyunSttService(config);
            case "volcengine" -> new VolcengineSttService(config);
            default -> {
                var service = initializeVosk();
                if (service == null) {
                    // If vosk create failed, return fallback stt service
                    if (fallbackProvider != null && serviceCache.containsKey(fallbackProvider)) {
                        yield serviceCache.get(fallbackProvider);
                    }
                    throw new RuntimeException("Create vosk service failed");
                }
                yield service;
            }
        };
    }

    public void removeCache(ConfigBO config) {
        // 对于API服务，使用"provider:configId"作为缓存键，确保每个配置使用独立的服务实例
        Integer configId = config.getConfigId();
        String provider = config.getProvider();
        String cacheKey = provider + ":" + (configId != null ? configId : "default");

        if ("aliyun-nls".equals(provider)) {
            AliyunNlsSttService.clearClientCache(configId);
        }

        serviceCache.remove(cacheKey);
    }
}
