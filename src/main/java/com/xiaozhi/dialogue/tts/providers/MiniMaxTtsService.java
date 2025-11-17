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
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HexFormat;

@Slf4j
public class MiniMaxTtsService implements TtsService {

    private static final String PROVIDER_NAME = "minimax";

    private final String groupId;
    private final String apiKey;

    private final String outputPath;
    private final String voiceName;
    
    // 语音参数
    private final Float pitch;
    private final Float speed;

    private final OkHttpClient client = HttpUtil.client;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public MiniMaxTtsService(SysConfig config, String voiceName, Float pitch, Float speed, String outputPath) {
        this.groupId = config.getAppId();
        this.apiKey = config.getApiKey();
        this.voiceName = voiceName;
        this.pitch = pitch;
        this.speed = speed;
        this.outputPath = outputPath;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String audioFormat() {
        return "mp3";
    }

    @Override
    public String textToSpeech(String text) throws Exception {
        var output = Paths.get(outputPath, getAudioFileName()).toString();
        sendRequest(text, output);
        return output;
    }

    private void sendRequest(String text, String filepath) {
        // 创建请求参数
        var params = new Text2AudioParams(voiceName, text);
        
        // 设置语速（MiniMax范围 [0.5, 2]，与我们的范围一致，直接使用）
        params.voiceSetting.setSpeed(speed);
        
        // 设置音调（需要映射：我们的 [0.5, 2] → MiniMax的 [-12, 12]）
        // 映射公式：minimax_pitch = (our_pitch - 1.0) × 24
        int minimaxPitch = (int)Math.round((pitch - 1.0f) * 24);
        // 确保值在有效范围内
        minimaxPitch = Math.max(-12, Math.min(12, minimaxPitch));
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

        public Text2AudioParams(String voiceId, String text) {
            this("speech-02-hd", voiceId, text);
        }

        public Text2AudioParams(String model, String voiceId, String text) {
            this.model = model;
            this.text = text;
            this.audioSetting = new AudioSetting();
            this.voiceSetting = new VoiceSetting().setVoiceId(voiceId);
        }

        private String model;
        private String text;
        private boolean stream = false;
        private String languageBoost = "auto";
        private String outputFormat = "hex";
        private VoiceSetting voiceSetting;
        private AudioSetting audioSetting;

        @Data
        @Accessors(chain = true)
        public static class VoiceSetting {
            @JsonProperty("voice_id")
            private String voiceId;
            private double speed = 1;
            private double vol = 1;
            private int pitch = 0;
            private String emotion = "happy";
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
        private Data data;
        @JsonProperty("base_resp")
        private BaseResp baseResp;

        record Data(int status, String audio) {
        }

        record BaseResp(@JsonProperty("status_code") int statusCode, @JsonProperty("status_msg") String statusMsg) {
        }
    }

}
