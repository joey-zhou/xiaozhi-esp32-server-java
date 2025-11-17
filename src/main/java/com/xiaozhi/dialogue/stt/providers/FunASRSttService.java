package com.xiaozhi.dialogue.stt.providers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.entity.SysConfig;

import org.springframework.util.StringUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * FunASR STT服务实现
 * <br/>
 * <a href="https://github.com/modelscope/FunASR/blob/main/runtime/docs/SDK_tutorial_online_zh.md">FunASR实时语音听写便捷部署教程</a>
 *  <br/>
 * <a href="https://github.com/modelscope/FunASR/blob/main/runtime/docs/SDK_advanced_guide_online_zh.md">FunASR实时语音听写服务开发指南</a>
 *  <br/>
 * <a href="https://www.funasr.com/static/offline/index.html">体验地址</a>
 */
public class FunASRSttService implements SttService {

    private static final Logger logger = LoggerFactory.getLogger(FunASRSttService.class);
    private static final String PROVIDER_NAME = "funasr";

    private static final String SPEAKING_START = "{\"mode\":\"online\",\"wav_name\":\"voice.wav\",\"is_speaking\":true,\"wav_format\":\"pcm\",\"chunk_size\":[5,10,5],\"itn\":true}";
    private static final String SPEAKING_END = "{\"is_speaking\": false}";
    private static final int QUEUE_TIMEOUT_MS = 100; // 队列等待超时时间
    private static final long RECOGNITION_TIMEOUT_MS = 90000; // 识别超时时间（90秒）

    private final String apiUrl;

    public FunASRSttService(SysConfig config) {
        this.apiUrl = config.getApiUrl();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String recognition(byte[] audioData) {
        logger.warn("不支持，请使用流式识别");
        return "";
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String streamRecognition(Sinks.Many<byte[]> audioSink) {
        // 使用阻塞队列存储音频数据
        BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        AtomicReference<String> finalResult = new AtomicReference<>("");
        CountDownLatch recognitionLatch = new CountDownLatch(1);
        
        // 订阅Sink并将数据放入队列
        audioSink.asFlux().subscribe(
            data -> audioQueue.offer(data),
            error -> {
                logger.error("音频流处理错误", error);
                isCompleted.set(true);
            },
            () -> isCompleted.set(true)
        );
        
        // 创建WebSocket客户端
        WebSocketClient webSocketClient = new WebSocketClient(URI.create(apiUrl)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                logger.debug("FunASR WebSocket连接已打开");
                send(SPEAKING_START);
                
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
                            
                            if (audioChunk != null && isOpen()) {
                                send(audioChunk);
                            }
                        }
                        
                        // 发送结束信号
                        if (isOpen()) {
                            send(SPEAKING_END);
                        }
                    } catch (Exception e) {
                        logger.error("发送音频数据时发生错误", e);
                    }
                });
            }

            @Override
            public void onMessage(String message) {
                try {
                    JSONObject jsonObject = JSON.parseObject(message);
                    if (jsonObject.getBoolean("is_final")) {
                        String text = jsonObject.getString("text");
                        finalResult.set(text);
                        recognitionLatch.countDown(); // 识别完成，释放锁
                    }
                } catch (Exception e) {
                    logger.error("解析FunASR响应失败", e);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                logger.info("FunASR WS关闭，原因：{}", reason);
                // 确保锁被释放
                recognitionLatch.countDown();
            }

            @Override
            public void onError(Exception ex) {
                logger.error("FunASR WS错误", ex);
                // 确保锁被释放
                recognitionLatch.countDown();
            }
        };

        try {
            // 连接WebSocket
            webSocketClient.connect();
            
            // 等待识别完成或超时
            boolean recognized = recognitionLatch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            if (!recognized) {

            }
        } catch (Exception e) {
            logger.error("FunASR识别过程中发生错误", e);
        } finally {
            // 关闭WebSocket连接
            if (webSocketClient.isOpen()) {
                webSocketClient.close();
            }
        }
        
        return finalResult.get();
    }
}