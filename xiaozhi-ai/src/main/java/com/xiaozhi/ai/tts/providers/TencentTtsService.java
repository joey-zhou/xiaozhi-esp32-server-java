package com.xiaozhi.ai.tts.providers;

import com.tencent.core.ws.Credential;
import com.tencent.core.ws.SpeechClient;
import com.tencent.ttsv2.*;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.XiaozhiTtsOptions;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.utils.AudioUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TencentTtsService implements TtsService {
    private static final String PROVIDER_NAME = "tencent";
    // 默认的腾讯云TTS WebSocket地址
    private static final String DEFAULT_TTS_REQ_URL = "wss://tts.cloud.tencent.com/stream_ws";
    // 识别超时时间（60秒）
    private static final long SYNTHESIS_TIMEOUT_MS = 60000;

    // 重试机制常量
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    // 腾讯云认证信息
    private String appId;
    private String secretId;
    private String secretKey;

    // 语音参数（voiceName, pitch, speed）
    private final XiaozhiTtsOptions options;

    // SpeechClient应用全局创建一个即可,生命周期可和整个应用保持一致
    private static final SpeechClient speechClient = new SpeechClient(DEFAULT_TTS_REQ_URL);

    public TencentTtsService(ConfigBO config, String voiceName, Double pitch, Double speed, String outputPath) {
        this.options = XiaozhiTtsOptions.builder().voiceName(voiceName).pitch(pitch).speed(speed).build();
        this.appId = config.getAppId();
        this.secretId = config.getApiKey();
        this.secretKey = config.getApiSecret();
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
    public String audioFormat() {
        return "mp3";
    }

    private Flux<byte[]> stream(String text) throws Exception {
        if (text == null || text.isEmpty()) {
            log.warn("文本内容为空！");
            return Flux.empty();
        }

        // 腾讯云 SDK 的 start() 是非阻塞的，音频数据通过 onAudioResult 回调异步推送。
        // 使用 Sinks.Many 替代 CountDownLatch.await()，避免阻塞 Reactor 调度器线程。
        return Flux.defer(() -> {
            Sinks.Many<byte[]> dataSink = Sinks.many().unicast().onBackpressureBuffer();

            Credential credential = new Credential(appId, secretId, secretKey);
            SpeechSynthesizerRequest request = new SpeechSynthesizerRequest();
            request.setText(text);

            int voiceType = Integer.parseInt(getVoiceName());
            request.setVoiceType(voiceType);

            // 将我们的参数（0.5-2.0）映射到腾讯云的参数（-2到6）
            float tencentSpeed = (float) ((getSpeed() - 0.5) * (4.0 / 1.5) - 2.0);
            tencentSpeed = Math.max(-2.0f, Math.min(6.0f, tencentSpeed));
            request.setSpeed(tencentSpeed);

            request.setVolume(0f);
            request.setCodec("pcm");
            request.setSampleRate(AudioUtils.SAMPLE_RATE);
            request.setSessionId(UUID.randomUUID().toString());

            SpeechSynthesizerListener listener = new SpeechSynthesizerListener() {
                @Override
                public void onSynthesisStart(SpeechSynthesizerResponse response) {}

                @Override
                public void onAudioResult(ByteBuffer buffer) {
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    dataSink.tryEmitNext(data);
                }

                @Override
                public void onTextResult(SpeechSynthesizerResponse response) {}

                @Override
                public void onSynthesisEnd(SpeechSynthesizerResponse response) {
                    dataSink.tryEmitComplete();
                }

                @Override
                public void onSynthesisFail(SpeechSynthesizerResponse response) {
                    String message = response.getMessage() != null ? response.getMessage() : "未知错误";
                    log.error("腾讯云TTS合成失败 - SessionId: {}, 错误: {}",
                            response.getSessionId(), message);
                    dataSink.tryEmitError(new Exception(message));
                }
            };

            // 创建语音合成器（synthesizer不可重复使用，每次合成需要重新生成新对象）
            SpeechSynthesizer[] synthRef = new SpeechSynthesizer[1];
            try {
                SpeechSynthesizer synthesizer = new SpeechSynthesizer(speechClient, credential, request, listener);
                synthRef[0] = synthesizer;
                synthesizer.start();
            } catch (Exception e) {
                log.error("腾讯云TTS合成过程中发生错误", e);
                return Flux.error(e);
            }

            return dataSink.asFlux()
                    .timeout(Duration.ofMillis(SYNTHESIS_TIMEOUT_MS))
                    .doFinally(signal -> {
                        SpeechSynthesizer synth = synthRef[0];
                        if (synth != null) {
                            try { synth.stop(); } catch (Exception e) { log.warn("停止腾讯云TTS时发生错误", e); }
                            try { synth.close(); } catch (Exception e) { log.error("关闭腾讯云TTS合成器时发生错误", e); }
                        }
                    });
        }).retryWhen(Retry.fixedDelay(MAX_RETRY_ATTEMPTS - 1, Duration.ofMillis(RETRY_DELAY_MS)))
          .doOnError(e -> log.error("腾讯云流式语音合成失败，已达到最大重试次数", e));
    }

    @Override
    public Path textToSpeech(String text) throws Exception {
        if (text == null || text.isEmpty()) {
            log.warn("文本内容为空！");
            return null;
        }

        int attempts = 0;
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                // 使用流式接口合成音频，然后合并所有音频片段
                ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
                final Exception[] error = new Exception[1];

                stream(text).subscribe(audioData -> {
                    if (audioData != null) {
                        try {
                            audioBuffer.write(audioData);
                        } catch (Exception e) {
                            log.error("写入音频数据失败", e);
                            error[0] = e;
                        }
                    }
                });

                // 如果有错误，抛出异常
                if (error[0] != null) {
                    throw error[0];
                }

                // 将合并后的PCM音频数据转换为WAV格式并保存
                byte[] pcmData = audioBuffer.toByteArray();
                if (pcmData.length == 0) {
                    log.warn("合成的音频数据为空");
                    return null;
                }

                // 转换为WAV并保存
                String filePath = AudioUtils.saveAsWav(pcmData);

                return Path.of(filePath);

            } catch (Exception e) {
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    log.warn("腾讯云语音合成失败，正在重试 ({}/{}): {}", attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("重试等待被中断", ie);
                        throw e;
                    }
                } else {
                    log.error("腾讯云语音合成失败，已达到最大重试次数", e);
                    throw new Exception("非流式语音合成失败", e);
                }
            }
        }
        throw new Exception("语音合成失败");
    }

}
