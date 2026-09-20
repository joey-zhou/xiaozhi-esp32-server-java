package com.xiaozhi.ai.stt;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.ai.stt.providers.*;
import com.xiaozhi.common.port.ProviderTokenClient;
import com.xiaozhi.common.model.bo.ConfigBO;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
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

    /** SenseVoice 单次解码用的 onnxruntime 线程数；解码并发路数由分到的核预算除以它得到，调大它并发路数随之减少 */
    @Value("${xiaozhi.stt.sense-voice.num-threads:2}")
    private int senseVoiceNumThreads = 2;

    // 缓存已初始化的服务：key format: "provider:configId"
    private final Map<String, SttService> serviceCache = new ConcurrentHashMap<>();

    /** 本地 provider：sherpa-onnx 跑 SenseVoice，是首选；Vosk 是模型没就位时的兜底 */
    static final String SENSE_VOICE_PROVIDER = "sherpa-onnx";
    static final String VOSK_PROVIDER = "vosk";

    /** 启动时按模型是否就位决定的本地默认 provider，两个都没有为 null */
    private volatile String localDefaultProvider;

    /**
     * 应用启动时加载本地识别模型：SenseVoice 优先，没有再试 Vosk
     */
    @PostConstruct
    public void initializeDefaultSttService() {
        if (initializeSenseVoice() != null) {
            localDefaultProvider = SENSE_VOICE_PROVIDER;
            log.info("默认语音识别服务为 sherpa-onnx SenseVoice");
            return;
        }
        if (initializeVosk() != null) {
            localDefaultProvider = VOSK_PROVIDER;
            log.info("默认语音识别服务为 Vosk");
            return;
        }
        log.warn("本地语音识别模型都未加载成功，未配置第三方 STT 的角色将无法识别语音");
    }

    /** 启动时加载成功的本地 provider，两个模型都没有为 null；角色页据此显示"本地识别"到底是哪个模型 */
    public String getLocalDefaultProvider() {
        return localDefaultProvider;
    }

    /**
     * 初始化 SenseVoice。模型目录缺失是常态（没下载）
     */
    private synchronized SttService initializeSenseVoice() {
        if (serviceCache.containsKey(SENSE_VOICE_PROVIDER)) {
            return serviceCache.get(SENSE_VOICE_PROVIDER);
        }
        try {
            var service = new SenseVoiceSttService(
                    runtimePathConfig.resolveSenseVoiceModelDir().toString(), senseVoiceNumThreads);
            service.initialize();
            serviceCache.put(SENSE_VOICE_PROVIDER, service);
            return service;
        } catch (Throwable e) {
            log.warn("SenseVoice STT 服务初始化失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 初始化Vosk服务
     */
    private synchronized SttService initializeVosk() {
        if (serviceCache.containsKey(VOSK_PROVIDER)) {
            return serviceCache.get(VOSK_PROVIDER);
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
            
            serviceCache.put(VOSK_PROVIDER, voskService);
            log.info("Vosk STT服务初始化成功");
            return voskService;
        } catch (Throwable e) {
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
            // 启动时两个模型都没加载成功时留空，交给 localDefaultOrThrow 再试一次并把两个原因一起报出来
            String provider = localDefaultProvider != null ? localDefaultProvider : "";
            config = new ConfigBO().setProvider(provider).setConfigId(-1);
        }

        // 对于API服务，使用"provider:configId"作为缓存键，确保每个配置使用独立的服务实例
        var cacheKey = config.getProvider() + ":" + config.getConfigId();
        final ConfigBO finalConfig = config;

        // 使用 computeIfAbsent 确保原子性操作，避免并发创建多个实例
        return serviceCache.computeIfAbsent(cacheKey, k -> createApiService(finalConfig));
    }

    /**
     * 按配置新建一个一次性的STT服务，既不读缓存也不写缓存，用完即弃。
     * <p>
     * 仅供未保存的临时配置（如配置测试）使用。这类配置的 configId 不指向真实配置，
     * 走 {@link #getSttService(ConfigBO)} 会把临时凭据留在缓存里，被后续真实会话取到。
     * 本地 provider 无凭据，仍返回共享实例。
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
            case SENSE_VOICE_PROVIDER -> senseVoiceOrThrow();
            case VOSK_PROVIDER -> voskOrThrow();
            case "" -> localDefaultOrThrow();
            case null -> localDefaultOrThrow();
            default -> throw new IllegalArgumentException("不支持的 STT provider: " + config.getProvider());
        };
        return service;
    }

    private SttService senseVoiceOrThrow() {
        var service = initializeSenseVoice();
        if (service == null) {
            throw new IllegalStateException("本地语音识别(sherpa-onnx SenseVoice)不可用，模型目录未就位，请下载模型或为该角色配置第三方 STT");
        }
        return service;
    }

    private SttService voskOrThrow() {
        var vosk = initializeVosk();
        if (vosk == null) {
            // 不得回退到其它配置创建出的实例，那会把别的租户的第三方凭据借出去
            throw new IllegalStateException("本地语音识别(Vosk)不可用，请为该角色配置第三方 STT");
        }
        return vosk;
    }

    /** 未指定 provider 的配置按本地默认走：SenseVoice 优先，其次 Vosk */
    private SttService localDefaultOrThrow() {
        var service = initializeSenseVoice();
        if (service != null) {
            return service;
        }
        var vosk = initializeVosk();
        if (vosk == null) {
            throw new IllegalStateException("本地语音识别不可用，SenseVoice 与 Vosk 模型都未就位，请下载模型或为该角色配置第三方 STT");
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
