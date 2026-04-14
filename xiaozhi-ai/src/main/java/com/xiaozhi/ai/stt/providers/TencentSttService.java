package com.xiaozhi.ai.stt.providers;

import com.tencent.asrv2.SpeechRecognizer;
import com.tencent.asrv2.SpeechRecognizerListener;
import com.tencent.asrv2.SpeechRecognizerRequest;
import com.tencent.asrv2.SpeechRecognizerResponse;
import com.tencent.core.ws.Credential;
import com.tencent.core.ws.SpeechClient;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.ai.utils.HttpUtil;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;

public class TencentSttService implements SttService {
    private static final Logger logger = LoggerFactory.getLogger(TencentSttService.class);
    private static final String PROVIDER_NAME = "tencent";
    private static final String API_URL = "https://asr.tencentcloudapi.com";
    private static final int QUEUE_TIMEOUT_MS = 100; // 队列等待超时时间
    private static final long RECOGNITION_TIMEOUT_MS = 90000; // 识别超时时间（90秒）

    // 使用腾讯云SDK的默认URL
    private static final String WS_API_URL = "wss://asr.cloud.tencent.com/asr/v2/";

    private String secretId;
    private String secretKey;
    private String appId;

    private final static OkHttpClient client = HttpUtil.client;

    // 全局共享的SpeechClient实例
    private final SpeechClient speechClient = new SpeechClient(WS_API_URL);

    // 存储当前活跃的识别会话
    private final ConcurrentHashMap<String, SpeechRecognizer> activeRecognizers = new ConcurrentHashMap<>();

    static {
        Thread.startVirtualThread(() -> {
            try {
                Request request = new Request.Builder().url(API_URL).head().build();
                Response response = client.newCall(request).execute();
                response.close(); // 不读取内容，仅建立连接，用以提速后续的请求
            } catch (Exception e) {
                logger.error("初始化TencentSttService STT服务时发生错误", e);
            }
        });
    }

    public TencentSttService(ConfigBO config) {
        if (config != null) {
            this.secretId = config.getApiKey();
            this.secretKey = config.getApiSecret();
            this.appId = config.getAppId();
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public SttResult stream(Flux<byte[]> audioSink) {
        // 检查配置是否已设置
        if (secretId == null || secretKey == null || appId == null) {
            logger.error("腾讯云语音识别配置未设置，无法进行识别");
            return null;
        }

        // 使用阻塞队列存储音频数据
        BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        AtomicReference<String> finalResult = new AtomicReference<>("");
        CountDownLatch recognitionLatch = new CountDownLatch(1);
        
        // 订阅Sink并将数据放入队列
        audioSink.subscribe(
            data -> audioQueue.offer(data),
            error -> {
                logger.error("音频流处理错误", error);
                isCompleted.set(true);
            },
            () -> isCompleted.set(true)
        );

        // 生成唯一的语音ID
        String voiceId = UUID.randomUUID().toString();

        try {
            // 创建腾讯云凭证
            Credential credential = new Credential(appId, secretId, secretKey);

            // 创建识别请求
            SpeechRecognizerRequest request = SpeechRecognizerRequest.init();
            request.setEngineModelType("16k_zh"); // 16k采样率中文模型
            request.setVoiceFormat(1); // PCM格式
            request.setVoiceId(voiceId);

            // 创建识别监听器
            SpeechRecognizerListener listener = new SpeechRecognizerListener() {
                private final StringBuilder textBuilder = new StringBuilder();
                
                @Override
                public void onRecognitionStart(SpeechRecognizerResponse response) {
                    logger.debug("腾讯云识别开始 - VoiceId: {}", voiceId);
                }

                @Override
                public void onSentenceBegin(SpeechRecognizerResponse response) {
                    // 句子开始，可以不处理
                }

                @Override
                public void onRecognitionResultChange(SpeechRecognizerResponse response) {
                    // 非稳态结果，可能会变化
                    if (response.getResult() != null && response.getResult().getVoiceTextStr() != null) {
                        String text = response.getResult().getVoiceTextStr();
                        if (!text.isEmpty()) {
                            // 更新当前识别结果
                            synchronized (textBuilder) {
                                textBuilder.setLength(0);
                                textBuilder.append(text);
                            }
                        }
                    }
                }

                @Override
                public void onSentenceEnd(SpeechRecognizerResponse response) {
                    // 稳态结果，不再变化
                    if (response.getResult() != null && response.getResult().getVoiceTextStr() != null) {
                        String text = response.getResult().getVoiceTextStr();
                        if (!text.isEmpty()) {
                            // 更新最终结果
                            synchronized (textBuilder) {
                                textBuilder.setLength(0);
                                textBuilder.append(text);
                            }
                            finalResult.set(text);
                        }
                    }
                }

                @Override
                public void onRecognitionComplete(SpeechRecognizerResponse response) {
                    // 识别完成，获取最终结果
                    if (response.getResult() != null && response.getResult().getVoiceTextStr() != null) {
                        String text = response.getResult().getVoiceTextStr();
                        if (!text.isEmpty()) {
                            finalResult.set(text);
                        } else {
                            // 如果最终结果为空，使用之前积累的结果
                            synchronized (textBuilder) {
                                if (textBuilder.length() > 0) {
                                    finalResult.set(textBuilder.toString());
                                }
                            }
                        }
                    }
                    
                    // 释放锁，表示识别完成
                    recognitionLatch.countDown();
                    
                    // 从活跃识别器中移除
                    activeRecognizers.remove(voiceId);
                }

                @Override
                public void onFail(SpeechRecognizerResponse response) {
                    logger.error("识别失败 - VoiceId: {}, 错误: {}", voiceId,
                            response.getMessage() != null ? response.getMessage() : "未知错误");
                    
                    // 释放锁，表示识别失败
                    recognitionLatch.countDown();
                    
                    // 从活跃识别器中移除
                    activeRecognizers.remove(voiceId);
                }

                @Override
                public void onMessage(SpeechRecognizerResponse response) {
                    // 可以记录所有消息，但不需要特别处理
                }
            };

            // 创建识别器
            SpeechRecognizer recognizer = new SpeechRecognizer(speechClient, credential, request, listener);

            // 存储到活跃识别器映射中
            activeRecognizers.put(voiceId, recognizer);

            // 启动识别器
            recognizer.start();

            // 标记是否已经发送了停止信号
            AtomicBoolean stopSent = new AtomicBoolean(false);

            // 启动虚拟线程发送音频数据
            Thread.startVirtualThread(() -> {
                try {
                    while (!isCompleted.get() || !audioQueue.isEmpty()) {
                        byte[] audioChunk = null;
                        try {
                            audioChunk = audioQueue.poll(QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            logger.warn("音频数据队列等待被中断", e);
                            Thread.currentThread().interrupt(); // 重新设置中断标志
                            break;
                        }
                        
                        if (audioChunk != null && activeRecognizers.containsKey(voiceId)) {
                            try {
                                recognizer.write(audioChunk);
                            } catch (Exception e) {
                                logger.error("发送音频数据时发生错误 - VoiceId: {}", voiceId, e);
                                break;
                            }
                        }
                    }
                    
                    // 发送停止信号
                    if (activeRecognizers.containsKey(voiceId) && !stopSent.getAndSet(true)) {
                        try {
                            recognizer.stop();
                        } catch (Exception e) {
                            logger.error("停止识别器时发生错误 - VoiceId: {}", voiceId, e);
                        }
                    }
                } catch (Exception e) {
                    logger.error("处理音频流时发生错误 - VoiceId: {}", voiceId, e);
                }
            });

            // 等待识别完成或超时
            boolean recognized = recognitionLatch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            if (!recognized) {
                // 超时后清理资源
                if (activeRecognizers.containsKey(voiceId)) {
                    try {
                        recognizer.stop();
                        recognizer.close();
                        activeRecognizers.remove(voiceId);
                    } catch (Exception e) {
                        logger.error("清理超时识别器资源时发生错误 - VoiceId: {}", voiceId, e);
                    }
                }
            } else {
                // 正常完成后也关闭recognizer释放资源
                try {
                    recognizer.close();
                } catch (Exception e) {
                    logger.error("关闭识别器时发生错误 - VoiceId: {}", voiceId, e);
                }
            }

        } catch (Exception e) {
            logger.error("创建语音识别会话时发生错误", e);
        }
        
        return SttResult.textOnly(finalResult.get());
    }

    // 在服务关闭时释放资源
    public void shutdown() {
        // 关闭所有活跃的识别器
        activeRecognizers.forEach((id, recognizer) -> {
            try {
                recognizer.stop();
                recognizer.close();
            } catch (Exception e) {
                logger.error("关闭识别器时发生错误 - VoiceId: {}", id, e);
            }
        });
        activeRecognizers.clear();

        // 关闭SpeechClient
        speechClient.shutdown();
    }

}