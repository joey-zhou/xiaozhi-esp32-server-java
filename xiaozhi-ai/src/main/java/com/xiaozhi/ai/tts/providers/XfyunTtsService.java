package com.xiaozhi.ai.tts.providers;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.XiaozhiTtsOptions;
import com.xiaozhi.common.model.bo.ConfigBO;

import cn.xfyun.api.TtsClient;
import cn.xfyun.model.response.TtsResponse;
import cn.xfyun.service.tts.AbstractTtsWebSocketListener;
import okhttp3.Response;
import okhttp3.WebSocket;

import lombok.extern.slf4j.Slf4j;
/**
 * 讯飞语音合成服务
 */
@Slf4j
public class XfyunTtsService implements TtsService {
    private static final String PROVIDER_NAME = "xfyun";
    // 识别超时时间（60秒）
    private static final long RECOGNITION_TIMEOUT_MS = 60000;

    // 重试机制常量
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final XiaozhiTtsOptions options;

    // 音频输出路径
    private String outputPath;

    // appid, apiKey, apiSecret是在开放平台控制台(https://console.xfyun.cn/)获得
    private String appId;
    private String apiKey;
    private String apiSecret;

    public XfyunTtsService(ConfigBO config, String voiceName, Double pitch, Double speed, String outputPath) {
        this.options = XiaozhiTtsOptions.builder().voiceName(voiceName).pitch(pitch).speed(speed).build();
        this.outputPath = outputPath;
        this.appId = config.getAppId();
        this.apiKey = config.getApiKey();
        this.apiSecret = config.getApiSecret();
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
                File file = new File(audioFilePath);
                // 发送POST请求
                boolean success = sendRequest(text, file);

                if (success) {
                    return Path.of(audioFilePath);
                } else {
                    throw new Exception("语音合成失败");
                }
            } catch (Exception e) {
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    log.warn("讯飞语音合成失败，正在重试 ({}/{}): {}", attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("重试等待被中断", ie);
                        throw e;
                    }
                } else {
                    log.error("讯飞语音合成失败，已达到最大重试次数", e);
                    throw e;
                }
            }
        }
        throw new Exception("语音合成失败");
    }

    /**
     * 发送POST请求到 xfyun，获取语音合成结果
     */
    private boolean sendRequest(String text, File file) throws Exception {
        CountDownLatch recognitionLatch = new CountDownLatch(1);
        AtomicBoolean succeeded = new AtomicBoolean(false);
        try {
            int xfyunSpeed = toXfyunScale(getSpeed());
            int xfyunPitch = toXfyunScale(getPitch());

            // 设置合成参数
            TtsClient ttsClient = new TtsClient.Builder()
                    .signature(appId, apiKey, apiSecret)
                    .aue("lame")
                    .vcn(getVoiceName())
                    .speed(xfyunSpeed)
                    .pitch(xfyunPitch)
                    .build();
            ttsClient.send(text, new AbstractTtsWebSocketListener() {
                //返回格式为音频文件的二进制数组bytes
                @Override
                public void onSuccess(byte[] bytes) {
                    try {
                        try (FileOutputStream outputStream = new FileOutputStream(file)) {
                            outputStream.write(bytes);
                            outputStream.flush();
                        }

                        // 验证文件已成功写入
                        if (!file.exists() || file.length() == 0) {
                            throw new RuntimeException("音频文件写入失败");
                        }
                        succeeded.set(true);

                    } catch (Exception e) {
                        log.error("写入音频文件失败", e);
                        throw new RuntimeException(e);
                    } finally {
                        // 最后确保countDown被调用
                        recognitionLatch.countDown();
                    }
                }

                //授权失败通过throwable.getMessage()获取对应错误信息
                @Override
                public void onFail(WebSocket webSocket, Throwable throwable, Response response) {
                    log.error("xfyun tts fail，原因：{}", throwable.getMessage());
                    recognitionLatch.countDown();
                }

                //业务失败通过ttsResponse获取错误码和错误信息
                @Override
                public void onBusinessFail(WebSocket webSocket, TtsResponse ttsResponse) {
                    log.error(ttsResponse.toString());
                    recognitionLatch.countDown();
                }
            });
        } catch (Exception e) {
            log.error("发送TTS请求时发生错误", e);
            recognitionLatch.countDown();
            throw new Exception("发送TTS请求失败", e);
        }
        // 等待语音合成完成或超时
        boolean recognized = recognitionLatch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!recognized) {
            log.warn("讯飞云语音合成超时");
            return false;
        }
        return succeeded.get();
    }


    /**
     * 把本系统的语速/音调（0.5-2.0，1.0 为常速）非线性映射到讯飞的 0-100 刻度：
     * 0.5→0、1.0→50（讯飞默认）、2.0→100。
     * <p>
     * 结果必须钳到 0-100 再交给 SDK：角色的语速/音调是用户自由填写的数值，
     * 越界值算出来会是负数或大于 100，讯飞对超范围参数的行为未定义。
     * 取值缺省时按常速处理，避免拆箱 NPE。
     */
    static int toXfyunScale(Double rate) {
        double value = rate == null ? 1.0d : rate;
        int scaled = value <= 1.0d
                ? (int) Math.round((value - 0.5d) * 100d)
                : (int) Math.round(50d + (value - 1.0d) * 50d);
        return Math.max(0, Math.min(100, scaled));
    }
}