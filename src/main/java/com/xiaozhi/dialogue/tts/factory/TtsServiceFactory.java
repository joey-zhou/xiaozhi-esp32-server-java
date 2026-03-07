package com.xiaozhi.dialogue.tts.factory;

import com.xiaozhi.dialogue.token.TokenService;
import com.xiaozhi.dialogue.token.factory.TokenServiceFactory;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.dialogue.tts.providers.*;
import com.xiaozhi.entity.SysConfig;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TtsServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(TtsServiceFactory.class);

    // 缓存已初始化的服务：键为"provider:configId:voiceName"格式，确保音色变化时创建新实例
    private final Map<String, TtsService> serviceCache = new ConcurrentHashMap<>();

    @Resource
    private TokenServiceFactory tokenServiceFactory;

    // 语音生成文件保存地址
    public static final String OUTPUT_PATH = "audio/";

    // 默认服务提供商名称
    private static final String DEFAULT_PROVIDER = "edge";

    // 默认 EDGE TTS 服务默认语音名称
    private static final String DEFAULT_VOICE = "zh-CN-XiaoyiNeural";

    /**
     * 获取默认TTS服务
     */
    public TtsService getDefaultTtsService() {
        var config = new SysConfig().setProvider(DEFAULT_PROVIDER);
        return getTtsService(config, TtsServiceFactory.DEFAULT_VOICE, 1.0f, 1.0f);
    }

    // 创建缓存键（包含pitch和speed）
    private String createCacheKey(SysConfig config, String provider, String voiceName, Float pitch, Float speed) {
        Integer configId = -1;
        if (config != null && config.getConfigId() != null) {
            configId = config.getConfigId();
        }
        return provider + ":" + configId + ":" + voiceName + ":" + pitch + ":" + speed;
    }

    /**
     * 根据配置获取TTS服务（带pitch和speed参数）
     */
    public TtsService getTtsService(SysConfig config, String voiceName, Float pitch, Float speed) {
        final SysConfig finalConfig = !ObjectUtils.isEmpty(config) ? config : new SysConfig().setProvider(DEFAULT_PROVIDER);
        String provider = finalConfig.getProvider();
        String cacheKey = createCacheKey(finalConfig, provider, voiceName, pitch, speed);

        // 使用 computeIfAbsent 确保原子性操作，避免并发创建多个实例
        return serviceCache.computeIfAbsent(cacheKey, k -> createApiService(finalConfig, voiceName, pitch, speed));
    }

    /**
     * 根据配置创建API类型的TTS服务（带pitch和speed参数）
     */
    private TtsService createApiService(SysConfig config, String voiceName, Float pitch, Float speed) {
        // Make sure output dir exists
        String outputPath = OUTPUT_PATH;
        ensureOutputPath(outputPath);

        return switch (config.getProvider()) {
            case "aliyun" -> new AliyunTtsService(config, voiceName, pitch, speed, outputPath);
            case "aliyun-nls" -> {
                // 为NLS创建阿里云Token服务
                TokenService aliyunTokenService = tokenServiceFactory.getTokenService(config);
                yield new AliyunNlsTtsService(config, voiceName, pitch, speed, outputPath, aliyunTokenService);
            }
            case "volcengine" -> new VolcengineTtsService(config, voiceName, pitch, speed, outputPath);
            case "xfyun" -> new XfyunTtsService(config, voiceName, pitch, speed, outputPath);
            case "minimax" -> new MiniMaxTtsService(config, voiceName, pitch, speed, outputPath);
            case "tencent" -> new TencentTtsService(config, voiceName, pitch, speed, outputPath);
            case "sherpa-onnx" -> new SherpaOnnxTtsService(config, voiceName, pitch, speed, outputPath);
            default -> new EdgeTtsService(voiceName, pitch, speed, outputPath);
        };
    }


    private void ensureOutputPath(String outputPath) {
        File dir = new File(outputPath);
        if (!dir.exists()) dir.mkdirs();
    }

    public void removeCache(SysConfig config) {
        if (config == null) {
            return;
        }

        String provider = config.getProvider();
        Integer configId = config.getConfigId();

        // 如果是阿里云NLS，需要额外清理NlsClient缓存
        if ("aliyun-nls".equals(provider)) {
            AliyunNlsTtsService.clearClientCache(configId);
        }

        // 如果是sherpa-onnx，需要额外清理模型缓存
        if ("sherpa-onnx".equals(provider) && config.getApiUrl() != null) {
            SherpaOnnxTtsService.clearModelCache(config.getApiUrl());
        }

        // 遍历缓存的所有键，找到匹配的键并移除
        serviceCache.keySet().removeIf(key -> {
            String[] parts = key.split(":");
            if (parts.length < 5) {  // 新格式是 provider:configId:voiceName:pitch:speed
                return false;
            }
            String keyProvider = parts[0];
            String keyConfigId = parts[1];

            // 检查provider和configId是否匹配
            return keyProvider.equals(provider) && keyConfigId.equals(String.valueOf(configId));
        });

    }
}