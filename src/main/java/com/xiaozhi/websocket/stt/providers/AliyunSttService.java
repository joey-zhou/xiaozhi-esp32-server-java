package com.xiaozhi.websocket.stt.providers;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.websocket.stt.SttService;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.nio.ByteBuffer;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;

public class AliyunSttService implements SttService {
    private static final Logger logger = LoggerFactory.getLogger(AliyunSttService.class);
    private static final String PROVIDER_NAME = "aliyun";

    private String apiKey;

    public AliyunSttService(SysConfig config) {
        this.apiKey = config.getApiKey();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String recognition(byte[] audioData) {
        // 单次识别暂未实现，可以根据需要添加
        logger.warn("阿里云单次识别未实现，请使用流式识别");
        return null;
    }

    public Flux<String> streamRecognition(Flux<byte[]> audioStream) {
        Sinks.Many<String> resultSink = Sinks.many().multicast().onBackpressureBuffer();
        
        Flowable<ByteBuffer> rxAudioStream = Flowable.create(emitter -> {
            audioStream.subscribe(
                bytes -> {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    emitter.onNext(buffer);
                },
                emitter::onError,
                emitter::onComplete
            );
        }, BackpressureStrategy.BUFFER);

        // 创建识别参数
        RecognitionParam param = RecognitionParam.builder()
                .model("paraformer-realtime-v2")
                .format("pcm") // 默认使用PCM格式，可以根据实际情况调整
                .sampleRate(AudioUtils.SAMPLE_RATE)
                .apiKey(apiKey)
                .build();

        // 创建识别器
        Recognition recognizer = new Recognition();
        
        // 在单独的线程中执行流式识别，避免阻塞
        // 使用Reactor的Schedulers来管理线程
        Schedulers.boundedElastic().schedule(() -> {
            try {
                recognizer.streamCall(param, rxAudioStream)
                    .blockingForEach(result -> {
                        if (result.isSentenceEnd()) {
                            String text = result.getSentence().getText();
                            logger.info("识别结果（完成）: {}", text);
                            resultSink.tryEmitNext(text);
                        } else {
                            String text = result.getSentence().getText();
                            logger.debug("识别结果（中间）: {}", text);
                            resultSink.tryEmitNext(text);
                        }
                    });
                
                // 识别完成
                resultSink.tryEmitComplete();
            } catch (Exception e) {
                logger.error("流式语音识别失败", e);
                resultSink.tryEmitError(e);
            }
        });

        return resultSink.asFlux();
    }

}