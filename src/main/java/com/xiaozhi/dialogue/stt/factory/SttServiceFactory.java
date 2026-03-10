package com.xiaozhi.dialogue.stt.factory;

import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.dialogue.stt.providers.*;
import com.xiaozhi.dialogue.token.TokenService;
import com.xiaozhi.dialogue.token.factory.TokenServiceFactory;
import com.xiaozhi.entity.SysConfig;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SttServiceFactory implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SttServiceFactory.class);
    
    @Resource
    private TokenServiceFactory tokenServiceFactory;

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

    public void initializeDefaultSttService() {
        logger.info("正在初始化默认语音识别服务(Vosk)...");
        initializeVosk();
        if (voskInitialized) {
            logger.info("默认语音识别服务(Vosk)初始化成功，可直接使用");
        } else {
            logger.warn("默认语音识别服务(Vosk)初始化失败，将在需要时尝试使用备选服务");
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
            var voskService = new VoskSttService();
            voskService.initialize();
            
            // 检查模型是否真正加载成功
            if (voskService instanceof VoskSttService && !((VoskSttService)voskService).isModelLoaded()) {
                throw new Exception("Vosk model was not properly loaded");
            }
            
            serviceCache.put(DEFAULT_PROVIDER, voskService);
            voskInitialized = true;
            logger.info("Vosk STT服务初始化成功");
            return voskService;
        } catch (Exception e) {
            voskInitialized = false;
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
    public SttService getSttService(SysConfig config) {
        if (config == null) {
            config = new SysConfig().setProvider(DEFAULT_PROVIDER).setConfigId(-1);
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
     * 根据配置创建API类型的STT服务
     */
    private SttService createApiService(@Nonnull SysConfig config) {
        return switch (config.getProvider()) {
            case "tencent" -> new TencentSttService(config);
            case "aliyun" -> new AliyunSttService(config);
            case "aliyun-nls" -> {
                // 为NLS创建阿里云Token服务
                TokenService aliyunTokenService = tokenServiceFactory.getTokenService(config);
                yield new AliyunNlsSttService(config, aliyunTokenService);
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

    public void removeCache(SysConfig config) {
        // 对于API服务，使用"provider:configId"作为缓存键，确保每个配置使用独立的服务实例
        Integer configId = config.getConfigId();
        String provider = config.getProvider();
        String cacheKey = provider + ":" + (configId != null ? configId : "default");
        serviceCache.remove(cacheKey);
    }

    @Override
    public void run(String... args) throws Exception {
        initializeDefaultSttService();
    }
}