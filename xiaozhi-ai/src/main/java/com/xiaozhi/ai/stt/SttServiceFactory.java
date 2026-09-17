package com.xiaozhi.ai.stt;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.ai.stt.providers.*;
import com.xiaozhi.common.port.ProviderTokenClient;
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
    private ProviderTokenClient tokenClient;

    @Resource
    private RuntimePathConfig runtimePathConfig;

    // 缓存已初始化的服务：key format: "provider:configId"
    private final Map<String, SttService> serviceCache = new ConcurrentHashMap<>();

    // 默认服务提供商名称
    private static final String DEFAULT_PROVIDER = "vosk";

    // 标记Vosk是否初始化成功
    private boolean voskInitialized = false;

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
            log.warn("默认语音识别服务(Vosk)初始化失败，未配置第三方 STT 的角色将无法识别语音");
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
                throw new Exception("Vosk 模型加载失败");
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
        final ConfigBO finalConfig = config;

        // 使用 computeIfAbsent 确保原子性操作，避免并发创建多个实例
        // 经 self 调用，让 @Counted 走 Spring 代理，否则同类内自调用会绕过 AOP
        return serviceCache.computeIfAbsent(cacheKey, k -> createApiService(finalConfig));
    }

    /**
     * 按配置新建一个一次性的STT服务，既不读缓存也不写缓存，用完即弃。
     * <p>
     * 仅供未保存的临时配置（如配置测试）使用。这类配置的 configId 不指向真实配置，
     * 走 {@link #getSttService(ConfigBO)} 会把临时凭据留在缓存里，被后续真实会话取到。
     * 本地 vosk 无凭据，仍返回共享实例。
     */
    public SttService createTransientSttService(@Nonnull ConfigBO config) {
        return createApiService(config);
    }

    /**
     * 根据配置创建API类型的STT服务
     */
    public SttService createApiService(@Nonnull ConfigBO config) {
        SttService service = switch (config.getProvider()) {
            case "tencent" -> new TencentSttService(config);
            case "aliyun" -> new AliyunSttService(config);
            case "aliyun-nls" -> new AliyunNlsSttService(config, tokenClient);
            case "funasr" -> new FunASRSttService(config);
            case "xfyun" -> new XfyunSttService(config);
            case "volcengine" -> new VolcengineSttService(config);
            case "vosk", "" -> voskOrThrow();
            case null -> voskOrThrow();
            default -> throw new IllegalArgumentException("不支持的 STT provider: " + config.getProvider());
        };
        return service;
    }

    private SttService voskOrThrow() {
        var vosk = initializeVosk();
        if (vosk == null) {
            // 不得回退到其它配置创建出的实例，那会把别的租户的第三方凭据借出去
            throw new IllegalStateException("默认语音识别服务(Vosk)不可用，请为该角色配置第三方 STT");
        }
        return vosk;
    }

    public void removeCache(ConfigBO config) {
        Integer configId = config.getConfigId();
        // provider 可能已被运维改成别的值，旧 key 的 provider 段对不上；不再比较 provider，
        // 只按 configId 段清理，否则切走 provider 后旧实例/旧连接永远清不掉
        serviceCache.keySet().removeIf(k -> {
            int idx = k.indexOf(':');
            return idx >= 0 && k.substring(idx + 1).equals(String.valueOf(configId));
        });
        // evictClient 对不存在的 configId 是 no-op，不用再判断当前 provider 是不是 aliyun-nls：
        // 旧 provider 是 aliyun-nls、新 provider 不是时，也要能把旧连接清掉
        AliyunNlsSttService.clearClientCache(configId);
    }
}
