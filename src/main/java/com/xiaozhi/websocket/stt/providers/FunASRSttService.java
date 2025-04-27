package com.xiaozhi.websocket.stt.providers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.websocket.stt.SttService;
import org.apache.commons.lang3.StringUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.net.URI;

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
        return StringUtils.EMPTY;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    public Flux<String> streamRecognition(Flux<byte[]> audioStream) {
        // 创建结果接收器
        Sinks.Many<String> respSink = Sinks.many().multicast().onBackpressureBuffer();

        WebSocketClient webSocketClient = new WebSocketClient(URI.create(apiUrl)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                send(SPEAKING_START);
                audioStream.subscribeOn(Schedulers.boundedElastic())
                        .subscribe(this::send,
                                respSink::tryEmitError,
                                () -> {
                                    send(SPEAKING_END);
                                });
            }

            @Override
            public void onMessage(String message) {
                JSONObject jsonObject = JSON.parseObject(message);
                if (jsonObject.getBoolean("is_final")) {
                    respSink.tryEmitNext(jsonObject.getString("text"));
                    respSink.tryEmitComplete();
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                logger.info("FunASR WS close，reason：{}", reason);
            }

            @Override
            public void onError(Exception ex) {
                logger.info("FunASR WS onError", ex);
                respSink.tryEmitError(ex);
            }
        };

        webSocketClient.connect();

        return respSink.asFlux()
                .doOnCancel(() -> {
                    if (webSocketClient.isOpen()) {
                        webSocketClient.close();
                    }
                })
                .doOnTerminate(() -> {
                    if (webSocketClient.isOpen()) {
                        webSocketClient.close();
                    }
                });
    }

}
