package com.xiaozhi.ai.tts.providers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.XiaozhiTtsOptions;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.ai.utils.HttpUtil;

import okhttp3.*;
import okio.BufferedSource;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VolcengineTtsService implements TtsService {
    private static final String PROVIDER_NAME = "volcengine";
    /** 单向流式合成接口（HTTP Chunked 分块返回），用于非流式的一次性合成 */
    private static final String API_URL = "https://openspeech.bytedance.com/api/v3/tts/unidirectional";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** 豆包语音合成大模型 2.0：内置音色（uranus 系列） */
    private static final String RESOURCE_ID_TTS_2_0 = "seed-tts-2.0";

    /** 火山 v3 的成功状态码（合成结束时随最后一个分块返回）。 */
    private static final int CODE_SUCCESS = 20000000;

    // 重试机制常量
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    // 音频输出路径
    private String outputPath;

    // API相关：v3 统一使用新版控制台的 API Key 鉴权，不再需要 appId
    private String accessToken; // 对应 apiKey

    // 语音参数（voiceName, pitch, speed）
    private final XiaozhiTtsOptions options;

    private final OkHttpClient client = HttpUtil.client;

    public VolcengineTtsService(ConfigBO config, String voiceName, Double pitch, Double speed, String outputPath) {
        this.options = XiaozhiTtsOptions.builder().voiceName(voiceName).pitch(pitch).speed(speed).build();
        this.outputPath = outputPath;
        this.accessToken = config.getApiKey();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public XiaozhiTtsOptions getOptions() {
        return options;
    }

    /**
     * 选择与当前音色匹配的资源ID。资源ID与音色类型不匹配时服务端会返回 55000000 错误。
     * <p>
     * 仅支持 2.0：内置音色统一走 seed-tts-2.0。
     * 1.0 资源（volc.service_type.10029）只接受旧版控制台鉴权，与新版 API Key 不兼容，故不再支持。
     */
    String resolveResourceId() {
        return RESOURCE_ID_TTS_2_0;
    }

    /**
     * 倍率（0.5~2.0，默认 1.0）换算为 v3 的 speech_rate / loudness_rate 整数值。
     * <p>取值范围 [-50, 100]，其中 100 代表 2.0 倍，-50 代表 0.5 倍，故 0 代表原速。
     */
    static int toV3Rate(Double ratio) {
        if (ratio == null) {
            return 0;
        }
        long value = Math.round((ratio - 1.0d) * 100.0d);
        return (int) Math.max(-50, Math.min(100, value));
    }

    /**
     * 音调倍率换算为 v3 的 post_process.pitch（半音数，取值范围 [-12, 12]）。
     * <p>音高是对数关系：semitone = 12 * log2(ratio)，恰好让 0.5 倍→-12、2.0 倍→+12。
     */
    static int toV3Pitch(Double ratio) {
        if (ratio == null || ratio <= 0) {
            return 0;
        }
        long value = Math.round(12.0d * (Math.log(ratio) / Math.log(2)));
        return (int) Math.max(-12, Math.min(12, value));
    }

    /**
     * 构建 v3 的音频参数，pcm 场景不传 bit_rate（该参数仅对 mp3 生效）。
     */
    private JsonObject buildAudioParams() {
        JsonObject audioParams = new JsonObject();
        audioParams.addProperty("format", "pcm");
        audioParams.addProperty("sample_rate", AudioUtils.SAMPLE_RATE);
        audioParams.addProperty("speech_rate", toV3Rate(getSpeed()));
        return audioParams;
    }

    /**
     * 音调非默认值时追加 post_process 节点。
     */
    private void appendPostProcess(JsonObject reqParams) {
        int pitch = toV3Pitch(getPitch());
        if (pitch != 0) {
            JsonObject postProcess = new JsonObject();
            postProcess.addProperty("pitch", pitch);
            reqParams.add("post_process", postProcess);
        }
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
                // 生成音频文件名
                String audioFileName = getAudioFileName();
                String audioFilePath = outputPath + audioFileName;

                // 发送POST请求
                boolean success = sendRequest(text, audioFilePath);

                if (success) {
                    return Path.of(audioFilePath);
                } else {
                    throw new Exception("语音合成失败");
                }
            } catch (Exception e) {
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    log.warn("火山语音合成失败，正在重试 ({}/{}): {}", attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("重试等待被中断", ie);
                        throw e;
                    }
                } else {
                    log.error("火山语音合成失败，已达到最大重试次数", e);
                    throw e;
                }
            }
        }
        throw new Exception("语音合成失败");
    }

    /**
     * 调用单向流式合成接口获取语音合成结果。
     * <p>
     * 该接口基于 HTTP Chunked 协议，响应是逐块返回的 JSON（每块携带一段 base64 音频），
     * 必须按块累加拼接，否则只能拿到第一片音频导致语音被截断。
     */
    private boolean sendRequest(String text, String audioFilePath) throws Exception {
        try {
            JsonObject reqParams = new JsonObject();
            reqParams.addProperty("text", text);
            reqParams.addProperty("speaker", getVoiceName());
            reqParams.add("audio_params", buildAudioParams());
            appendPostProcess(reqParams);

            JsonObject requestJson = new JsonObject();
            requestJson.add("req_params", reqParams);

            RequestBody requestBody = RequestBody.create(JSON, requestJson.toString());
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Api-Key", accessToken)
                    .addHeader("X-Api-Resource-Id", resolveResourceId())
                    .addHeader("X-Api-Request-Id", UUID.randomUUID().toString())
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    log.error("TTS请求失败: {} {}, 错误信息: {}, 原始内容: {}", response.code(), response.message(), errorBody, text);
                    return false;
                }
                if (response.body() == null) {
                    log.error("TTS响应体为空");
                    return false;
                }

                ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();
                BufferedSource source = response.body().source();
                String line;
                while ((line = source.readUtf8Line()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonObject chunk;
                    try {
                        chunk = JsonParser.parseString(line).getAsJsonObject();
                    } catch (Exception e) {
                        log.warn("跳过无法解析的响应分块: {}", line);
                        continue;
                    }
                    // 合成结束时服务端会回一个成功码分块（20000000 OK），不是错误
                    if (chunk.has("code")) {
                        int chunkCode = chunk.get("code").getAsInt();
                        if (chunkCode != 0 && chunkCode != CODE_SUCCESS) {
                            log.error("TTS请求返回错误: code={}, message={}", chunkCode,
                                    chunk.has("message") ? chunk.get("message").getAsString() : "");
                            return false;
                        }
                    }
                    if (chunk.has("data") && !chunk.get("data").isJsonNull()) {
                        String base64Audio = chunk.get("data").getAsString();
                        if (!base64Audio.isEmpty()) {
                            pcmBuffer.write(Base64.getDecoder().decode(base64Audio));
                        }
                    }
                }

                if (pcmBuffer.size() == 0) {
                    log.error("TTS响应中未找到音频数据，原始内容: {}", text);
                    return false;
                }

                // 接口返回裸 PCM，补上 WAV 头后落盘（采样率与声道数同 AudioUtils 约定）
                AudioUtils.saveAsWav(Path.of(audioFilePath), pcmBuffer.toByteArray());
                return true;
            }
        } catch (Exception e) {
            log.error("发送TTS请求时发生错误", e);
            throw new Exception("发送TTS请求失败", e);
        }
    }
}
