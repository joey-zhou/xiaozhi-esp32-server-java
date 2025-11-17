package com.xiaozhi.dialogue.tts.providers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.HttpUtil;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;
import java.util.UUID;

public class VolcengineTtsService implements TtsService {
    private static final Logger logger = LoggerFactory.getLogger(VolcengineTtsService.class);

    private static final String PROVIDER_NAME = "volcengine";
    private static final String API_URL = "https://openspeech.bytedance.com/api/v1/tts";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // 音频名称
    private String voiceName;

    // 音频输出路径
    private String outputPath;

    // API相关
    private String appId;
    private String accessToken; // 对应 apiKey
    
    // 语音参数
    private Float pitch;
    private Float speed;

    private final OkHttpClient client = HttpUtil.client;

    public VolcengineTtsService(SysConfig config, String voiceName, Float pitch, Float speed, String outputPath) {
        this.voiceName = voiceName;
        this.pitch = pitch;
        this.speed = speed;
        this.outputPath = outputPath;
        this.appId = config.getAppId();
        this.accessToken = config.getApiKey();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String textToSpeech(String text) throws Exception {
        if (text == null || text.isEmpty()) {
            logger.warn("文本内容为空！");
            return null;
        }

        try {
            // 生成音频文件名
            String audioFileName = getAudioFileName();
            String audioFilePath = outputPath + audioFileName;

            // 发送POST请求
            boolean success = sendRequest(text, audioFilePath);

            if (success) {
                return audioFilePath;
            } else {
                throw new Exception("语音合成失败");
            }
        } catch (Exception e) {
            logger.error("语音合成时发生错误！", e);
            throw e;
        }
    }

    /**
     * 发送POST请求到火山引擎API，获取语音合成结果
     */
    private boolean sendRequest(String text, String audioFilePath) throws Exception {
        try {
            // 构建请求参数
            JsonObject requestJson = new JsonObject();

            // app部分
            JsonObject app = new JsonObject();
            app.addProperty("appid", appId);
            app.addProperty("token", accessToken);
            app.addProperty("cluster", "volcano_tts");
            requestJson.add("app", app);

            // user部分
            JsonObject user = new JsonObject();
            user.addProperty("uid", UUID.randomUUID().toString());
            requestJson.add("user", user);

            // audio部分
            JsonObject audio = new JsonObject();
            audio.addProperty("voice_type", voiceName);
            audio.addProperty("encoding", "wav");
            audio.addProperty("speed_ratio", speed);
            audio.addProperty("volume_ratio", 1.0);
            audio.addProperty("pitch_ratio", pitch);
            audio.addProperty("rate", AudioUtils.SAMPLE_RATE);
            requestJson.add("audio", audio);

            // request部分
            JsonObject request_JsonObject = new JsonObject();
            request_JsonObject.addProperty("reqid", UUID.randomUUID().toString());
            request_JsonObject.addProperty("text", text);
            request_JsonObject.addProperty("text_type", "plain");
            request_JsonObject.addProperty("operation", "query");
            request_JsonObject.addProperty("with_frontend", 1);
            request_JsonObject.addProperty("frontend_type", "unitTson");
            requestJson.add("request", request_JsonObject);

            // 使用Bearer Token鉴权方式
            String bearerToken = "Bearer; " + accessToken; // 注意分号是火山引擎的特殊格式

            RequestBody requestBody = RequestBody.create(JSON, requestJson.toString());

            // 设置请求头和请求体
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", bearerToken) // 添加Authorization头
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    logger.error("TTS请求失败: {} {}, 错误信息: {}, 原始内容: {}", response.code(), response.message(), errorBody, text);
                    return false;
                }

                // 解析响应
                if (response.body() != null) {
                    String responseBody = response.body().string();
                    JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                    // 检查响应是否包含错误
                    if (jsonResponse.has("code") && jsonResponse.get("code").getAsInt() != 3000) {
                        logger.error("TTS请求返回错误: code={}, message={}",
                                jsonResponse.get("code").getAsInt(),
                                jsonResponse.get("message").getAsString());
                        return false;
                    }

                    // 获取音频数据
                    if (jsonResponse.has("data")) {
                        String base64Audio = jsonResponse.get("data").getAsString();
                        byte[] audioData = Base64.getDecoder().decode(base64Audio);

                        // 保存音频文件
                        File audioFile = new File(audioFilePath);
                        try (FileOutputStream fout = new FileOutputStream(audioFile)) {
                            fout.write(audioData);
                        }

                        return true;
                    } else {
                        logger.error("TTS响应中未找到音频数据: {}", responseBody);
                        return false;
                    }
                } else {
                    logger.error("TTS响应体为空");
                    return false;
                }
            }
        } catch (Exception e) {
            logger.error("发送TTS请求时发生错误", e);
            throw new Exception("发送TTS请求失败", e);
        }
    }

}