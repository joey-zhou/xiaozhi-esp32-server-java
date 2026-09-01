package com.xiaozhi.ai.stt.providers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xiaozhi.common.annotation.MonitoredOperation;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.common.model.bo.ConfigBO;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
/**
 * FunASR STT服务实现
 * <br/>
 * <a href="https://github.com/modelscope/FunASR/blob/main/runtime/docs/SDK_tutorial_online_zh.md">FunASR实时语音听写便捷部署教程</a>
 *  <br/>
 * <a href="https://github.com/modelscope/FunASR/blob/main/runtime/docs/SDK_advanced_guide_online_zh.md">FunASR实时语音听写服务开发指南</a>
 *  <br/>
 * <a href="https://www.funasr.com/static/offline/index.html">体验地址</a>
 */
@Slf4j
public class FunASRSttService implements SttService {

    private static final String PROVIDER_NAME = "funasr";

    private static final String SPEAKING_START = "{\"mode\":\"2pass\",\"wav_name\":\"voice.wav\",\"is_speaking\":true,\"wav_format\":\"pcm\",\"chunk_size\":[5,10,5],\"itn\":true}";
    private static final String SPEAKING_END = "{\"is_speaking\": false}";
    private static final int QUEUE_TIMEOUT_MS = 100; // 队列等待超时时间
    // 上游未终结音频流时的兜底上限，需远大于设备上行抖动，否则弱网会截断用户没说完的话
    private static final long IDLE_TIMEOUT_MS = 5000;
    private static final long RECOGNITION_TIMEOUT_MS = 90000; // 识别超时时间（90秒）

    private final String apiUrl;

    public FunASRSttService(ConfigBO config) {
        this.apiUrl = config.getApiUrl();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * 中间结果旁路通知：只传非空文本，且回调异常绝不影响识别主流程
     */
    private void notifyPartialText(Consumer<String> onPartialText, String text) {
        if (onPartialText == null || text == null || text.isBlank()) {
            return;
        }
        try {
            onPartialText.accept(text);
        } catch (Exception e) {
            log.debug("中间识别结果回调异常，已忽略", e);
        }
    }

    @Override
    public SttResult stream(Flux<byte[]> audioSink) {
        return stream(audioSink, text -> {
        });
    }

    @MonitoredOperation(name = "xiaozhi.stt.stream")
    @Override
    public SttResult stream(Flux<byte[]> audioSink, Consumer<String> onPartialText) {
        // 使用阻塞队列存储音频数据
        BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        // 拼接所有2pass-offline离线修正结果
        StringBuilder offlineResult = new StringBuilder();
        AtomicReference<String> finalResult = new AtomicReference<>("");
        CountDownLatch recognitionLatch = new CountDownLatch(1);
        
        // 订阅Sink并将数据放入队列
        audioSink.subscribe(
            data -> audioQueue.offer(data),
            error -> {
                log.error("音频流处理错误", error);
                isCompleted.set(true);
            },
            () -> isCompleted.set(true)
        );
        
        // 创建WebSocket客户端
        WebSocketClient webSocketClient = new WebSocketClient(URI.create(apiUrl)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                log.debug("FunASR WebSocket连接已打开");
                send(SPEAKING_START);
                
                // 启动虚拟线程发送音频数据
                Thread.startVirtualThread(() -> {
                    try {
                        long idleMs = 0;
                        while (!isCompleted.get() || !audioQueue.isEmpty()) {
                            byte[] audioChunk = null;
                            try {
                                audioChunk = audioQueue.poll(QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                            } catch (InterruptedException e) {
                                log.warn("音频数据队列等待被中断", e);
                                Thread.currentThread().interrupt(); // 重新设置中断标志
                                break;
                            }

                            if (audioChunk == null) {
                                idleMs += QUEUE_TIMEOUT_MS;
                                // 上游未按预期终结音频流时的兜底，避免线程与连接一直挂着
                                if (idleMs >= IDLE_TIMEOUT_MS) {
                                    log.warn("音频流长时间无数据，主动结束识别");
                                    break;
                                }
                                continue;
                            }
                            idleMs = 0;
                            if (!isOpen()) {
                                break;
                            }
                            send(audioChunk);
                        }
                        
                        // 发送结束信号
                        if (isOpen()) {
                            send(SPEAKING_END);
                        }
                    } catch (Exception e) {
                        log.error("发送音频数据时发生错误", e);
                    }
                });
            }

            @Override
            public void onMessage(String message) {
                try {
                    JSONObject jsonObject = JSON.parseObject(message);
                    boolean isFinal = Boolean.TRUE.equals(jsonObject.getBoolean("is_final"));
                    String mode = jsonObject.getString("mode");
                    String text = jsonObject.getString("text");
                    // 中间结果旁路：2pass-online为实时增量文本，2pass-offline为分段离线修正文本，
                    // 两者都早于连接关闭到达，任意非空文本都说明用户确实开口说话了
                    notifyPartialText(onPartialText, text);
                    // 2pass模式：拼接每个离线修正片段（VAD可能将一句话分为多段）
                    if (isFinal && "2pass-offline".equals(mode)) {
                        if (text != null && !text.isEmpty()) {
                            offlineResult.append(text);
                        }
                        log.debug("FunASR 离线修正片段: {}", text);
                    }
                } catch (Exception e) {
                    log.error("解析FunASR响应失败", e);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                log.info("FunASR WS关闭，原因：{}", reason);
                // 连接关闭时，离线修正结果已全部收到，设置最终结果
                finalResult.set(offlineResult.toString());
                recognitionLatch.countDown();
            }

            @Override
            public void onError(Exception ex) {
                log.error("FunASR WS错误", ex);
                // 先设置已有的结果，再释放锁，避免主线程读到空结果
                finalResult.set(offlineResult.toString());
                recognitionLatch.countDown();
            }
        };

        try {
            // 连接WebSocket
            webSocketClient.connect();
            
            // 等待识别完成或超时
            boolean recognized = recognitionLatch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            if (!recognized) {
                log.warn("FunASR识别超时");
            }
        } catch (Exception e) {
            log.error("FunASR识别过程中发生错误", e);
        } finally {
            // 关闭WebSocket连接
            if (webSocketClient.isOpen()) {
                webSocketClient.close();
            }
        }
        
        return SttResult.textOnly(finalResult.get());
    }
}