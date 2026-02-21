package com.xiaozhi.dialogue.tts.providers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.HttpUtil;
import com.xiaozhi.utils.JsonUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HexFormat;

@Slf4j
public class MiniMaxTtsService implements TtsService {

    private static final String PROVIDER_NAME = "minimax";

    // 重试机制常量
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final String groupId;
    private final String apiKey;

    private final String outputPath;
    private final String voiceName;
    
    // 语音参数
    //private final Float pitch;
    private int minimaxPitch;
    private final Float speed;
    private final Float pitch;
    private final String model;

    private final OkHttpClient client = HttpUtil.client;
    public static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json; charset=utf-8";
    private static final MediaType JSON = MediaType.parse(APPLICATION_JSON_CHARSET_UTF_8);

    public MiniMaxTtsService(SysConfig config, String voiceName, Float pitch, Float speed, String outputPath) {
        this.groupId = config.getAppId();
        this.apiKey = config.getApiKey();
        this.voiceName = voiceName;
        this.pitch = pitch;
        this.speed = speed;
        this.outputPath = outputPath;
        this.model = config.getConfigName();
        // 设置音调（需要映射：我们的 [0.5, 2] → MiniMax的 [-12, 12]）
        // 映射公式：minimax_pitch = (our_pitch - 1.0) × 24
        minimaxPitch = (int)Math.round((pitch - 1.0f) * 24);
        // 确保值在有效范围内
        minimaxPitch = Math.max(-12, Math.min(12, minimaxPitch));
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
        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                var output = Paths.get(outputPath, getAudioFileName()).toString();
                sendRequest(text, output);
                return output;
            } catch (Exception e) {
                lastException = e;
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    log.warn("MiniMax语音合成失败，正在重试 ({}/{}): {}", attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("重试等待被中断", ie);
                        throw e;
                    }
                } else {
                    log.error("MiniMax语音合成失败，已达到最大重试次数", e);
                }
            }
        }
        throw lastException != null ? lastException : new Exception("语音合成失败");
    }

    private void sendRequest(String text, String filepath) {
        // 创建请求参数
        var params = new Text2AudioParams(model, voiceName, text);
        
        // 设置语速（MiniMax范围 [0.5, 2]，与我们的范围一致，直接使用）
        params.voiceSetting.setSpeed(speed);
        params.voiceSetting.setPitch(minimaxPitch);
        
        var request = new Request.Builder()
                .url("https://api.minimaxi.com/v1/t2a_v2?Groupid=%s".formatted(groupId))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer %s".formatted(apiKey)) // 添加Authorization头
                .post(RequestBody.create(JsonUtil.toJson(params), JSON))
                .build();

        try (var resp = client.newCall(request).execute()) {
            if (resp.isSuccessful()) {
                var respBody = JsonUtil.fromJson(resp.body().string(), Text2AudioResp.class);
                if (respBody.baseResp.statusCode == 0) {
                    var bytes = HexFormat.of().parseHex(respBody.data.audio);
                    Files.write(Paths.get(filepath), bytes);
                } else {
                    log.error("TTS失败 {}:{}", respBody.baseResp.statusCode, respBody.baseResp.statusMsg);
                }
            } else {
                log.error("TTS请求失败 {}", resp.body().string());
            }
        } catch (IOException e) {
            log.error("发送TTS请求时发生错误", e);
            throw new RuntimeException("发送TTS请求失败", e);
        }
    }

    @Data
    @Accessors(chain = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Text2AudioParams {

        public Text2AudioParams(String model, String voiceId, String text) {
            this.model = model;
            this.text = text;
            this.audioSetting = new AudioSetting();
            this.voiceSetting = new VoiceSetting().setVoiceId(voiceId);
        }

        private String model;
        private String text;
        private boolean stream = false;
        private StreamOptions streamOptions = new StreamOptions();
        private String languageBoost = "auto";
        private String outputFormat = "hex";
        private VoiceSetting voiceSetting;
        private AudioSetting audioSetting;

        @Data
        public static class StreamOptions{
            @JsonProperty("exclude_aggregated_audio")
            boolean excludeAggregatedAudio= true;
        }
        @Data
        @Accessors(chain = true)
        public static class VoiceSetting {
            @JsonProperty("voice_id")
            private String voiceId;
            private double speed = 1;
            private double vol = 1;
            private int pitch = 0;
            //private String emotion = "happy";
        }

        @Data
        public static class AudioSetting {
            @JsonProperty("sample_rate")
            private int sampleRate = 32000;
            private int bitrate = 128000;
            private String format = "mp3";
        }
    }

    @Data
    public static class Text2AudioResp {
        @JsonProperty("is_final")
        private boolean isFinal;
        @JsonProperty("session_id")
        private String sessionId;
        @JsonProperty("trace_id")
        private String traceId;
        @JsonProperty("event")
        private String event;
        @JsonProperty("data")
        private Data data;
        @JsonProperty("extra_info")
        private ExtraInfo extraInfo;
        @JsonProperty("base_resp")
        private BaseResp baseResp;

        /**
         * 在MiniMax 的 WebSocket协议中，Data不一定会有 status。 java record默认是全参构造函数。
         */
        @lombok.Data
        public static class Data {
            private int status;
            private String audio;
        }

        @lombok.Data
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        class ExtraInfo {
            @JsonProperty("audio_channel")
            private int audioChannel;
            @JsonProperty("audio_format")
            private String audioFormat;
            // 音频时长，精确到毫秒
            @JsonProperty("audio_length")
            private int audioLength;
            @JsonProperty("audio_sample_rate")
            private int audioSampleRate;
            // 音频文件大小，单位为字节
            @JsonProperty("audio_size")
            private int audioSize;
            // 音频比特率
            @JsonProperty("bitrate")
            private int bitrate;
            // 非法字符占比。非法字符不超过 10%（包含 10%），音频会正常生成并返回非法字符占比，超过进行报错
            @JsonProperty("invisible_character_ratio")
            private int invisibleCharacterRatio;
            // 计费字符数。本次语音生成的计费字符数
            @JsonProperty("usage_characters")
            private int usageCharacters;
            // 已发音的字数统计，包含汉字、数字、字母，不包含标点符号
            @JsonProperty("word_count")
            private int wordCount;
        }
        record BaseResp(@JsonProperty("status_code") int statusCode, @JsonProperty("status_msg") String statusMsg) {
        }
    }

}
