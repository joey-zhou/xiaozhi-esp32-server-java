package com.xiaozhi.ai.tts;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.port.ProviderTokenClient;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.ai.tts.providers.*;
import com.xiaozhi.common.model.bo.ConfigBO;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TtsServiceFactory {

    // instruction 是自由文本，键的组合数没有上限，必须给个硬上限兜底
    private static final int MAX_SERVICE_CACHE_SIZE = 500;

    /**
     * 缓存已初始化的服务：键为"provider:configId:voiceName:pitch:speed:instruction"格式。
     * LinkedHashMap 按访问顺序做 LRU，超过上限自动淘汰最久未用的实例；removeCache 按
     * provider+configId 前缀做的精确失效逻辑不变，两种失效方式互不影响、互为补充。
     */
    private final Map<String, TtsService> serviceCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, TtsService> eldest) {
                    return size() > MAX_SERVICE_CACHE_SIZE;
                }
            });

    @Resource
    private ProviderTokenClient tokenClient;

    @Resource
    private RuntimePathConfig runtimePathConfig;

    // 默认服务提供商名称
    private static final String DEFAULT_PROVIDER = "edge";

    // 默认 EDGE TTS 服务默认语音名称
    private static final String DEFAULT_VOICE = "zh-CN-XiaoyiNeural";

    /**
     * 获取默认TTS服务
     */
    public TtsService getDefaultTtsService() {
        var config = new ConfigBO().setProvider(DEFAULT_PROVIDER);
        return getTtsService(config, TtsServiceFactory.DEFAULT_VOICE, 1.0, 1.0);
    }

    // 创建缓存键（包含pitch和speed）
    private String createCacheKey(ConfigBO config, String provider, String voiceName, Double pitch, Double speed) {
        Integer configId = -1;
        if (config != null && config.getConfigId() != null) {
            configId = config.getConfigId();
        }
        return provider + ":" + configId + ":" + voiceName + ":" + normalizeRate(pitch) + ":" + normalizeRate(speed);
    }

    /**
     * 音调/语速归一化到 1 位小数再入键。合成效果分不出比这更细的差异，
     * 不归一化的话每个小数位都会占一条缓存；同一档内复用先建好的实例。
     */
    private static String normalizeRate(Double rate) {
        return rate == null ? "-" : Double.toString(Math.round(rate * 10) / 10.0);
    }

    /**
     * 根据配置获取TTS服务（带pitch和speed参数）
     */
    public TtsService getTtsService(ConfigBO config, String voiceName, Double pitch, Double speed) {
        final ConfigBO finalConfig = !ObjectUtils.isEmpty(config) ? config : new ConfigBO().setProvider(DEFAULT_PROVIDER);
        String provider = finalConfig.getProvider();
        String cacheKey = createCacheKey(finalConfig, provider, voiceName, pitch, speed);

        // 使用 computeIfAbsent 确保原子性操作，避免并发创建多个实例
        return serviceCache.computeIfAbsent(cacheKey, k -> createApiService(finalConfig, voiceName, pitch, speed));
    }

    /**
     * 根据配置创建API类型的TTS服务（带pitch和speed参数）
     */
    public TtsService createApiService(ConfigBO config, String voiceName, Double pitch, Double speed) {
        // Make sure output dir exists
        String outputPath = AudioUtils.AUDIO_PATH;
        ensureOutputPath(outputPath);

        TtsService ttsService = switch (config.getProvider()) {
            case "aliyun" -> new AliyunTtsService(config, voiceName, pitch, speed, outputPath);
            case "aliyun-nls" -> {
                yield new AliyunNlsTtsService(config, voiceName, pitch, speed, outputPath, tokenClient);
            }
            case "volcengine" -> new VolcengineTtsService(config, voiceName, pitch, speed, outputPath);
            case "xfyun" -> new XfyunTtsService(config, voiceName, pitch, speed, outputPath);
            case "minimax" -> new MiniMaxTtsService(config, voiceName, pitch, speed, outputPath);
            case "tencent" -> new TencentTtsService(config, voiceName, pitch, speed, outputPath);
            case "sherpa-onnx" -> new SherpaOnnxTtsService(
                    config,
                    voiceName,
                    pitch,
                    speed,
                    outputPath,
                    runtimePathConfig.resolveTtsModelsDir().toString()
            );
            default -> new EdgeTtsService(voiceName, pitch, speed, outputPath);
        };
        return ttsService;
    }

    private void ensureOutputPath(String outputPath) {
        File dir = new File(outputPath);
        if (!dir.exists()) dir.mkdirs();
    }

    /**
     * 清除指定配置的服务实例缓存。
     * <p>
     * 不清理 sherpa-onnx 的本地模型缓存。模型实例只由音色里的模型目录决定，与配置无关；
     * 且释放模型会 delete native 指针，与正在执行的合成并发即 use-after-free 崩进程。
     */
    public void removeCache(ConfigBO config) {
        if (config == null) {
            return;
        }

        String provider = config.getProvider();
        Integer configId = config.getConfigId();

        // 如果是阿里云NLS，需要额外清理NlsClient缓存
        if ("aliyun-nls".equals(provider)) {
            AliyunNlsTtsService.clearClientCache(configId);
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
