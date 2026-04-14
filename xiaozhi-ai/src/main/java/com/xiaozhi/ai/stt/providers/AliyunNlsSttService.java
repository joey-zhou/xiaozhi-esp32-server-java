package com.xiaozhi.ai.stt.providers;

import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.common.port.TokenResolver;
import com.xiaozhi.common.model.bo.ConfigBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 阿里云NLS实时语音识别服务
 * 使用阿里云智能语音交互SDK实现STT功能
 * 参考文档: https://help.aliyun.com/zh/isi/developer-reference/sdk-for-java-8
 */
public class AliyunNlsSttService implements SttService {
    private static final Logger logger = LoggerFactory.getLogger(AliyunNlsSttService.class);

    private static final String PROVIDER_NAME = "aliyun-nls";

    // 阿里云NLS服务的默认URL
    private static final String NLS_URL = "wss://nls-gateway.aliyuncs.com/ws/v1";

    // 超时时间
    private static final long RECOGNITION_TIMEOUT_MS = 90000; // 识别超时时间（90秒）

    /**
     * 全局NlsClient缓存（按configId共享）
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

    // Token管理器
    private final TokenResolver tokenResolver;

    public AliyunNlsSttService(ConfigBO config, TokenResolver tokenResolver) {
        this.config = config;
        this.tokenResolver = tokenResolver;
    }

    /**
     * 获取或创建NlsClient实例（支持连接复用）
     */
    private NlsClient getOrCreateClient() throws Exception {
        String currentToken = tokenResolver.getToken(config);
        if (currentToken == null) {
            throw new RuntimeException("无法获取阿里云Token");
        }

        Integer configId = config.getConfigId();
        int currentHash = currentToken.hashCode();

        CachedNlsClient cached = globalClientCache.get(configId);
        if (cached != null && cached.tokenHash == currentHash) {
            return cached.client;
        }

        return globalClientCache.compute(configId, (k, existing) -> {
            if (existing != null && existing.tokenHash == currentHash) {
                return existing;
            }
            if (existing != null) {
                try {
                    existing.client.shutdown();
                } catch (Exception e) {
                    logger.warn("关闭旧NlsClient失败", e);
                }
            }
            NlsClient newClient = new NlsClient(NLS_URL, currentToken);
            return new CachedNlsClient(newClient, currentHash);
        }).client;
    }

    /**
     * 清理指定configId的NlsClient缓存
     */
    public static void clearClientCache(Integer configId) {
        if (configId == null) {
            return;
        }
        CachedNlsClient removed = globalClientCache.remove(configId);
        if (removed != null) {
            try {
                removed.client.shutdown();
            } catch (Exception e) {
                logger.warn("关闭NlsClient失败", e);
            }
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public SttResult stream(Flux<byte[]> audioSink) {
        if (audioSink == null) {
            logger.error("音频数据流为空");
            return SttResult.textOnly("");
        }

        // 用于收集识别结果
        StringBuilder resultBuilder = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        // 用于标识识别是否完成
        AtomicBoolean recognitionCompleted = new AtomicBoolean(false);
        AtomicBoolean recognitionFailed = new AtomicBoolean(false);

        // 用于存储错误信息
        AtomicBoolean[] errorHolder = new AtomicBoolean[]{new AtomicBoolean(false)};

        NlsClient client = null;
        SpeechTranscriber transcriber = null;

        try {
            // 获取或复用NlsClient
            client = getOrCreateClient();

            // 创建识别监听器
            SpeechTranscriberListener listener = new SpeechTranscriberListener() {
                @Override
                public void onTranscriberStart(SpeechTranscriberResponse response) {
                }

                @Override
                public void onSentenceBegin(SpeechTranscriberResponse response) {
                }

                @Override
                public void onSentenceEnd(SpeechTranscriberResponse response) {
                    String text = response.getTransSentenceText();
                    if (text != null && !text.isEmpty()) {
                        synchronized (resultBuilder) {
                            resultBuilder.append(text);
                        }
                    }
                }

                @Override
                public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
                }

                @Override
                public void onTranscriptionComplete(SpeechTranscriberResponse response) {
                    logger.info("NLS实时识别完成 - TaskId: {}", response.getTaskId());
                    recognitionCompleted.set(true);
                    latch.countDown();
                }

                @Override
                public void onFail(SpeechTranscriberResponse response) {
                    logger.error("NLS实时识别失败 - TaskId: {}, Status: {}, StatusText: {}",
                            response.getTaskId(),
                            response.getStatus(),
                            response.getStatusText());
                    recognitionFailed.set(true);
                    errorHolder[0].set(true);
                    latch.countDown();
                }
            };

            // 创建语音识别器
            transcriber = new SpeechTranscriber(client, listener);

            // 设置AppKey
            transcriber.setAppKey(config.getApiKey());

            // 设置音频格式为PCM
            transcriber.setFormat(InputFormatEnum.PCM);

            // 设置采样率为16000Hz
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);

            // 启用中间结果
            transcriber.setEnableIntermediateResult(true);

            // 启用标点符号
            transcriber.setEnablePunctuation(true);

            // 启动识别
            transcriber.start();

            // 在新线程中发送音频数据
            final SpeechTranscriber finalTranscriber = transcriber;
            Thread sendThread = new Thread(() -> {
                try {
                    // 订阅音频流并发送数据
                    audioSink.subscribe(
                            audioChunk -> {
                                if (audioChunk != null && audioChunk.length > 0) {
                                    try {
                                        // 发送音频数据
                                        finalTranscriber.send(audioChunk);
                                    } catch (Exception e) {
                                        logger.error("发送音频数据失败", e);
                                    }
                                }
                            },
                            error -> {
                                logger.error("音频流处理错误", error);
                                errorHolder[0].set(true);
                                latch.countDown();
                            },
                            () -> {
                                try {
                                    // 音频流结束，停止识别
                                    finalTranscriber.stop();
                                } catch (Exception e) {
                                    logger.error("停止识别失败", e);
                                }
                            }
                    );
                } catch (Exception e) {
                    logger.error("处理音频流时发生错误", e);
                    errorHolder[0].set(true);
                    latch.countDown();
                }
            });
            sendThread.start();

            // 等待识别完成或超时
            if (!latch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                logger.error("NLS实时识别超时");
                return SttResult.textOnly("");
            }

            // 检查识别是否失败
            if (recognitionFailed.get() || errorHolder[0].get()) {
                logger.error("识别过程中发生错误");
                return SttResult.textOnly("");
            }

            // 返回识别结果
            String result;
            synchronized (resultBuilder) {
                result = resultBuilder.toString().trim();
            }
            logger.debug("阿里云NLS识别结果: {}", result);
            return SttResult.textOnly(result);

        } catch (Exception e) {
            logger.error("阿里云NLS实时识别失败", e);
            // 连接异常时清除缓存，下次调用时重建client
            globalClientCache.remove(config.getConfigId());
            return SttResult.textOnly("");
        } finally {
            // 只关闭transcriber，client由缓存统一管理复用，不在此处shutdown
            if (transcriber != null) {
                try {
                    transcriber.close();
                } catch (Exception e) {
                    logger.warn("关闭SpeechTranscriber失败", e);
                }
            }
        }
    }
}

