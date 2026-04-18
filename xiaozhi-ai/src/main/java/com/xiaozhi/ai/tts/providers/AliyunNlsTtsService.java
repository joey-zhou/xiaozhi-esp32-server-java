package com.xiaozhi.ai.tts.providers;

import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import com.xiaozhi.common.port.TokenResolver;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.XiaozhiTtsOptions;
import com.xiaozhi.common.model.bo.ConfigBO;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
/**
 * 阿里云NLS标准语音合成服务
 * 使用阿里云智能语音交互SDK实现TTS功能
 */
@Slf4j
public class AliyunNlsTtsService implements TtsService {
    private static final String PROVIDER_NAME = "aliyun-nls";

    // 阿里云NLS服务的默认URL
    private static final String NLS_URL = "wss://nls-gateway.aliyuncs.com/ws/v1";

    // 重试机制常量
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    /**
     * 全局NlsClient缓存（按configId共享）
     * 同一个configId的不同音色/语速配置可以共享同一个NlsClient连接
     */
    private static final ConcurrentHashMap<Integer, CachedNlsClient> globalClientCache = new ConcurrentHashMap<>();

    /**
     * 缓存的NlsClient包装类
     */
    private static class CachedNlsClient {
        final NlsClient client;
        final int tokenHash;

        CachedNlsClient(NlsClient client, int tokenHash) {
            this.client = client;
            this.tokenHash = tokenHash;
        }
    }

    // 阿里云配置
    private final ConfigBO config;
    private final XiaozhiTtsOptions options;
    private final String outputPath;

    // Token管理器
    private final TokenResolver tokenResolver;

    public AliyunNlsTtsService(ConfigBO config, String voiceName, Float pitch, Float speed, String outputPath, TokenResolver tokenResolver) {
        this.config = config;
        this.options = XiaozhiTtsOptions.builder().voiceName(voiceName).pitch(pitch).speed(speed).build();
        this.outputPath = outputPath;
        this.tokenResolver = tokenResolver;
    }

    /**
     * 获取或创建NlsClient实例（支持连接复用）
     * 使用全局缓存，按configId共享NlsClient
     */
    private NlsClient getOrCreateClient() throws Exception {
        String currentToken = tokenResolver.getToken(config);
        if (currentToken == null) {
            throw new RuntimeException("无法获取阿里云Token");
        }

        Integer configId = config.getConfigId();
        int currentHash = currentToken.hashCode();

        // 获取当前缓存的client
        CachedNlsClient cached = globalClientCache.get(configId);

        // 检查是否可以复用
        if (cached != null && cached.tokenHash == currentHash) {
            return cached.client;
        }

        // 需要创建新的client
        return globalClientCache.compute(configId, (k, existing) -> {
            // 双重检查：可能在等待期间已被其他线程创建
            if (existing != null && existing.tokenHash == currentHash) {
                return existing;
            }

            // 检查是否是token变化（首次创建existing为null，视为变化）
            boolean tokenChanged = (existing == null || existing.tokenHash != currentHash);

            if (tokenChanged) {
                // 关闭旧的client（如果有）
                if (existing != null) {
                    try {
                        existing.client.shutdown();
                    } catch (Exception e) {
                        log.warn("关闭旧NlsClient失败", e);
                    }
                }
            }

            // 创建新client
            NlsClient newClient = new NlsClient(NLS_URL, currentToken);
            return new CachedNlsClient(newClient, currentHash);
        }).client;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public XiaozhiTtsOptions getOptions() {
        return options;
    }

    @Override
    public Path textToSpeech(String text) throws Exception {
        if (text == null || text.isEmpty()) {
            log.warn("文本内容为空！");
            return null;
        }

        int attempts = 0;
        while (attempts < MAX_RETRY_ATTEMPTS) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            CountDownLatch latch = new CountDownLatch(1);
            NlsClient client = null;
            SpeechSynthesizer synthesizer = null;

            try {
                // 获取或复用NlsClient（连接复用）
                client = getOrCreateClient();

                synthesizer = new SpeechSynthesizer(client, new SpeechSynthesizerListener() {
                    @Override
                    public void onComplete(SpeechSynthesizerResponse response) {
                        latch.countDown();
                    }

                    @Override
                    public void onFail(SpeechSynthesizerResponse response) {
                        log.error("NLS语音合成失败 - TaskId: {}, Status: {}, StatusText: {}",
                                response.getTaskId(), response.getStatus(), response.getStatusText());
                        latch.countDown();
                    }

                    @Override
                    public void onMessage(ByteBuffer message) {
                        byte[] buffer = new byte[message.remaining()];
                        message.get(buffer);
                        try {
                            outputStream.write(buffer);
                        } catch (IOException e) {
                            log.error("写入音频数据失败", e);
                        }
                    }
                });

                // 设置appKey
                synthesizer.setAppKey(config.getApiKey());
                // 设置语音输出格式
                synthesizer.setFormat(OutputFormatEnum.WAV);
                // 设置采样率
                synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
                // 设置语音
                synthesizer.setVoice(getVoiceName());
                // 设置音量
                synthesizer.setVolume(100);

                // 设置语速和音调（映射：0.5-2.0 → -500~500）
                int nlsSpeed = (int)Math.round((getSpeed() - 1.0f) * 500);
                int nlsPitch = (int)Math.round((getPitch() - 1.0f) * 500);
                nlsSpeed = Math.max(-500, Math.min(500, nlsSpeed));
                nlsPitch = Math.max(-500, Math.min(500, nlsPitch));

                synthesizer.setSpeechRate(nlsSpeed);
                synthesizer.setPitchRate(nlsPitch);

                synthesizer.setText(text);
                synthesizer.start();

                // 设置超时时间，避免无限等待
                if (!latch.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.error("NLS语音合成超时");
                    throw new RuntimeException("语音合成超时");
                }

                // 检查是否有音频数据生成
                byte[] audioData = outputStream.toByteArray();
                if (audioData.length == 0) {
                    throw new RuntimeException("未生成音频数据");
                }

                String audioFileName = getAudioFileName();
                Path filePath = Path.of(outputPath, audioFileName);

                File outputDir = new File(outputPath);
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }

                try (FileOutputStream fileOutputStream = new FileOutputStream(filePath.toFile())) {
                    fileOutputStream.write(audioData);
                }

                return filePath;

            } catch (InterruptedException e) {
                // 线程被中断（用户打断对话），属于正常流程，不清除 NlsClient 缓存
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                attempts++;
                // 只关闭 synthesizer，client 由缓存统一管理复用，不在此处 shutdown
                if (synthesizer != null) {
                    try {
                        synthesizer.close();
                    } catch (Exception ex) {
                        log.warn("关闭SpeechSynthesizer失败", ex);
                    }
                }

                if (attempts < MAX_RETRY_ATTEMPTS) {
                    log.warn("阿里云NLS语音合成失败，正在重试 ({}/{}): {}", attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("重试等待被中断", ie);
                        // NLS 连接异常时清除缓存，下次调用时重建 client
                        globalClientCache.remove(config.getConfigId());
                        throw e;
                    }
                } else {
                    log.error("阿里云NLS语音合成失败，已达到最大重试次数: {}", e.getMessage(), e);
                    // NLS 连接异常时清除缓存，下次调用时重建 client
                    globalClientCache.remove(config.getConfigId());
                    throw e;
                }
            }
        }
        throw new Exception("语音合成失败");
    }

    /**
     * 清除指定configId的NlsClient缓存
     */
    public static void clearClientCache(Integer configId) {
        if (configId != null) {
            CachedNlsClient removed = globalClientCache.remove(configId);
            if (removed != null && removed.client != null) {
                removed.client.shutdown();
            }
        }
    }
}
