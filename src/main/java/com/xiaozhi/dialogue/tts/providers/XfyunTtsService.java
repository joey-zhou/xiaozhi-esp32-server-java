package com.xiaozhi.dialogue.tts.providers;

import cn.xfyun.api.TtsClient;
import cn.xfyun.model.response.TtsResponse;
import cn.xfyun.service.tts.AbstractTtsWebSocketListener;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.entity.SysConfig;
import okhttp3.Response;
import okhttp3.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class XfyunTtsService implements TtsService {
    private static final Logger logger = LoggerFactory.getLogger(XfyunTtsService.class);

    private static final String PROVIDER_NAME = "xfyun";
    // 识别超时时间（60秒）
    private static final long RECOGNITION_TIMEOUT_MS = 60000;

    // 音频名称
    private String voiceName;

    // 音频输出路径
    private String outputPath;

    // appid,apiKey,apiSecret是在开放平台控制台(https://console.xfyun.cn/)获得
    private String appId;
    private String apiKey;
    private String apiSecret;
    
    // 语音参数
    private Float pitch;
    private Float speed;

    public XfyunTtsService(SysConfig config, String voiceName, Float pitch, Float speed, String outputPath) {
        this.voiceName = voiceName;
        this.pitch = pitch;
        this.speed = speed;
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
    public String audioFormat() {
        return "mp3";
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
            File file = new File(audioFilePath);
            // 发送POST请求
            boolean success = sendRequest(text, file);

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
     * 发送POST请求到，获取语音合成结果
     */
    private boolean sendRequest(String text, File file) throws Exception {
        CountDownLatch recognitionLatch = new CountDownLatch(1);
        try {
            // 将我们的参数（0.5-2.0）映射到讯飞的参数（0-100）
            // 讯飞规则：0对应0.5倍，100对应2倍
            // 映射公式：xfyunValue = (ourValue - 0.5) * 100 / 1.5
            int xfyunSpeed = (int)Math.round((speed - 0.5f) * 100f / 1.5f);
            int xfyunPitch = (int)Math.round((pitch - 0.5f) * 100f / 1.5f);
            
            // 确保值在有效范围内
            xfyunSpeed = Math.max(0, Math.min(100, xfyunSpeed));
            xfyunPitch = Math.max(0, Math.min(100, xfyunPitch));
            
            // 设置合成参数
            TtsClient ttsClient = new TtsClient.Builder()
                    .signature(appId, apiKey, apiSecret)
                    .aue("lame")
                    .vcn(voiceName)
                    .speed(xfyunSpeed)
                    .pitch(xfyunPitch)
                    .build();
            ttsClient.send(text, new AbstractTtsWebSocketListener() {
                //返回格式为音频文件的二进制数组bytes
                @Override
                public void onSuccess(byte[] bytes) {
                    try {
                        FileOutputStream outputStream = new FileOutputStream(file);
                        outputStream.write(bytes);
                        outputStream.flush();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    recognitionLatch.countDown();
                }

                //授权失败通过throwable.getMessage()获取对应错误信息
                @Override
                public void onFail(WebSocket webSocket, Throwable throwable, Response response) {
                    logger.error("xfyun tts fail，原因：{}", throwable.getMessage());
                    recognitionLatch.countDown();
                }

                //业务失败通过ttsResponse获取错误码和错误信息
                @Override
                public void onBusinessFail(WebSocket webSocket, TtsResponse ttsResponse) {
                    logger.error(ttsResponse.toString());
                    recognitionLatch.countDown();
                }
            });
        } catch (Exception e) {
            logger.error("发送TTS请求时发生错误", e);
            recognitionLatch.countDown();
            throw new Exception("发送TTS请求失败", e);
        }
        // 等待识别完成或超时
        boolean recognized = recognitionLatch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!recognized) {
            logger.warn("讯飞云识别超时");
        }
        return true;
    }

}