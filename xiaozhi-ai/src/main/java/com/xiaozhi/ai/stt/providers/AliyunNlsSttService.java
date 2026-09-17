package com.xiaozhi.ai.stt.providers;

import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import com.xiaozhi.common.annotation.MonitoredOperation;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.common.port.TokenResolver;
import com.xiaozhi.common.model.bo.ConfigBO;
import reactor.core.publisher.Flux;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
/**
 * 阿里云NLS实时语音识别服务
 * 使用阿里云智能语音交互SDK实现STT功能
 * 参考文档: https://help.aliyun.com/zh/isi/developer-reference/sdk-for-java-8
 */
@Slf4j
public class AliyunNlsSttService implements SttService {
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
     * 用引用计数追踪正在使用它的识别会话数：token 轮换时旧client可能仍被并发请求占用，
     * 不能立即shutdown，只标记退休，等最后一个使用者释放引用后再真正关闭
     */
    private static class CachedNlsClient {
        final NlsClient client;
        final int tokenHash;
        final AtomicInteger refCount = new AtomicInteger(0);
        volatile boolean retiring = false;
        private boolean shutdownDone = false;

        CachedNlsClient(NlsClient client, int tokenHash) {
            this.client = client;
            this.tokenHash = tokenHash;
        }

        synchronized void retireWhenIdle() {
            retiring = true;
            shutdownIfIdle();
        }

        synchronized void release() {
            if (refCount.decrementAndGet() <= 0) {
                shutdownIfIdle();
            }
        }

        private void shutdownIfIdle() {
            if (retiring && refCount.get() <= 0 && !shutdownDone) {
                shutdownDone = true;
                try {
                    client.shutdown();
                } catch (Exception e) {
                    log.warn("关闭旧NlsClient失败", e);
                }
            }
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
     * 获取或创建NlsClient实例（支持连接复用），并占用一次引用计数。
     * 用完必须调用 releaseClient 归还，否则该client永远不会被判定为空闲而关闭。
     */
    private CachedNlsClient acquireClient() throws Exception {
        String currentToken = tokenResolver.getToken(config);
        if (currentToken == null) {
            throw new RuntimeException("无法获取阿里云Token");
        }

        Integer configId = config.getConfigId();
        int currentHash = currentToken.hashCode();

        // 整个判断+占用过程都放进 compute 的重映射函数里，靠 ConcurrentHashMap 按 key 加锁的特性
        // 保证不会跟同一 configId 的“标记退休”动作交错，避免引用计数和退休标记的竞态
        return globalClientCache.compute(configId, (k, existing) -> {
            if (existing != null && existing.tokenHash == currentHash) {
                existing.refCount.incrementAndGet();
                return existing;
            }
            if (existing != null) {
                existing.retireWhenIdle();
            }
            NlsClient newClient = new NlsClient(NLS_URL, currentToken);
            CachedNlsClient created = new CachedNlsClient(newClient, currentHash);
            created.refCount.incrementAndGet();
            return created;
        });
    }

    private static void releaseClient(CachedNlsClient cached) {
        if (cached != null) {
            cached.release();
        }
    }

    /**
     * 清理指定configId的NlsClient缓存
     */
    public static void clearClientCache(Integer configId) {
        evictClient(configId);
    }

    /**
     * 移出缓存并 shutdown，NlsClient 自带 Netty 线程组，只 remove 不 shutdown 会泄漏线程与连接
     */
    private static void evictClient(Integer configId) {
        if (configId == null) {
            return;
        }
        CachedNlsClient removed = globalClientCache.remove(configId);
        if (removed != null) {
            try {
                removed.client.shutdown();
            } catch (Exception e) {
                log.warn("关闭NlsClient失败", e);
            }
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * 安全地把中间识别结果通知给上层：空文本不回调，回调异常不影响识别主流程。
     */
    private void notifyPartial(Consumer<String> onPartialText, String text) {
        if (onPartialText == null || text == null || text.isEmpty()) {
            return;
        }
        try {
            onPartialText.accept(text);
        } catch (Exception e) {
            log.debug("中间识别结果回调异常，已忽略", e);
        }
    }

    @MonitoredOperation(name = "xiaozhi.stt.stream")
    @Override
    public SttResult stream(Flux<byte[]> audioSink) {
        return stream(audioSink, text -> {
        });
    }

    @MonitoredOperation(name = "xiaozhi.stt.stream")
    @Override
    public SttResult stream(Flux<byte[]> audioSink, Consumer<String> onPartialText) {
        if (audioSink == null) {
            log.error("音频数据流为空");
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
        CachedNlsClient cachedClient = null;

        try {
            // 获取或复用NlsClient
            cachedClient = acquireClient();
            client = cachedClient.client;

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
                    // 单句结束相对整轮识别仍属中间结果，一并通知上层
                    notifyPartial(onPartialText, text);
                }

                @Override
                public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
                    // 中间识别结果（已开启 setEnableIntermediateResult），仅作旁路通知，不参与最终结果拼装
                    notifyPartial(onPartialText, response.getTransSentenceText());
                }

                @Override
                public void onTranscriptionComplete(SpeechTranscriberResponse response) {
                    log.info("NLS实时识别完成 - TaskId: {}", response.getTaskId());
                    recognitionCompleted.set(true);
                    latch.countDown();
                }

                @Override
                public void onFail(SpeechTranscriberResponse response) {
                    log.error("NLS实时识别失败 - TaskId: {}, Status: {}, StatusText: {}",
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
                                        log.error("发送音频数据失败", e);
                                    }
                                }
                            },
                            error -> {
                                log.error("音频流处理错误", error);
                                errorHolder[0].set(true);
                                latch.countDown();
                            },
                            () -> {
                                try {
                                    // 音频流结束，停止识别
                                    finalTranscriber.stop();
                                } catch (Exception e) {
                                    log.error("停止识别失败", e);
                                }
                            }
                    );
                } catch (Exception e) {
                    log.error("处理音频流时发生错误", e);
                    errorHolder[0].set(true);
                    latch.countDown();
                }
            });
            sendThread.start();

            // 等待识别完成或超时
            if (!latch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                log.error("NLS实时识别超时");
                return SttResult.failure(SttResult.FAILURE_TIMEOUT);
            }

            // 检查识别是否失败
            if (recognitionFailed.get() || errorHolder[0].get()) {
                log.error("识别过程中发生错误");
                return SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR);
            }

            // 返回识别结果
            String result;
            synchronized (resultBuilder) {
                result = resultBuilder.toString().trim();
            }
            log.debug("阿里云NLS识别结果: {}", result);
            return SttResult.textOnly(result);

        } catch (Exception e) {
            log.error("阿里云NLS实时识别失败", e);
            // 连接异常时清除缓存，下次调用时重建client
            evictClient(config.getConfigId());
            return SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR);
        } finally {
            // 只关闭transcriber，client由缓存统一管理复用，不在此处shutdown
            if (transcriber != null) {
                try {
                    transcriber.close();
                } catch (Exception e) {
                    log.warn("关闭SpeechTranscriber失败", e);
                }
            }
            // transcriber已关闭，归还引用计数；若client已被标记退休且无人再用，这里会触发真正的shutdown
            releaseClient(cachedClient);
        }
    }
}

