package com.xiaozhi.dialogue.tts.providers;

import com.tencent.core.ws.SpeechClient;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.entity.SysConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class TencentTtsService implements TtsService {
    private static final Logger logger = LoggerFactory.getLogger(TencentTtsService.class);

    private static final String PROVIDER_NAME = "tencent";
    // 默认的腾讯云TTS WebSocket地址
    private static final String DEFAULT_TTS_REQ_URL = "wss://tts.cloud.tencent.com/stream_ws";
    // 识别超时时间（60秒）
    private static final long SYNTHESIS_TIMEOUT_MS = 60000;

    // 重试机制常量
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    // 音色名称
    private String voiceName;

    // 腾讯云认证信息
    private String appId;
    private String secretId;
    private String secretKey;

    // 语音参数
    private Float pitch;
    private Float speed;

    // SpeechClient应用全局创建一个即可,生命周期可和整个应用保持一致
    private static final SpeechClient speechClient = new SpeechClient(DEFAULT_TTS_REQ_URL);

    public TencentTtsService(SysConfig config, String voiceName, Float pitch, Float speed, String outputPath) {
        this.voiceName = voiceName;
        this.pitch = pitch;
        this.speed = speed;
        // outputPath暂时不使用，因为非流式TTS暂不支持
        this.appId = config.getAppId();
        this.secretId = config.getApiKey();
        this.secretKey = config.getApiSecret();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String getVoiceName() {
        return voiceName;
    }

    @Override
    public Float getSpeed() {
        return speed;
    }

    @Override
    public Float getPitch() {
        return pitch;
    }

    @Override
    public String audioFormat() {
        return "mp3";
    }

    @Override
    public String textToSpeech(String text) throws Exception {
        throw new UnsupportedOperationException("Unimplemented method 'textToSpeech'");
    }

}
