package com.xiaozhi.dialogue.tts.providers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.entity.SysConfig;

import cn.xfyun.api.TtsClient;
import cn.xfyun.model.response.TtsResponse;
import cn.xfyun.service.tts.AbstractTtsWebSocketListener;
import okhttp3.Response;
import okhttp3.WebSocket;

/**
 * 讯飞语音合成服务
 *
 */
public class XfyunTtsService implements TtsService {
    private static final Logger logger = LoggerFactory.getLogger(XfyunTtsService.class);

    private static final String PROVIDER_NAME = "xfyun";
    // 识别超时时间（60秒）
    private static final long RECOGNITION_TIMEOUT_MS = 60000;

    // 重试机制常量
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    // 音频名称
    private String voiceName;

    // 音频输出路径
    private String outputPath;

    // appid, apiKey, apiSecret是在开放平台控制台(https://console.xfyun.cn/)获得
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
        if (text == null || text.isEmpty()) {
            logger.warn("文本内容为空！");
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
                    return audioFilePath;
                } else {
                    throw new Exception("语音合成失败");
                }
            } catch (Exception e) {
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    logger.warn("讯飞语音合成失败，正在重试 ({}/{}): {}", attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("重试等待被中断", ie);
                        throw e;
                    }
                } else {
                    logger.error("讯飞语音合成失败，已达到最大重试次数", e);
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
        try {
            // 将我们的参数（0.5-2.0）非线性映射到讯飞的参数（0-100）
            // 映射规则：0.5→0，1.0→50（讯飞默认），2.0→100
            int xfyunSpeed;
            if (speed <= 1.0f) {
                xfyunSpeed = (int)Math.round((speed - 0.5f) * 100f);
            } else {
                xfyunSpeed = (int)Math.round(50f + (speed - 1.0f) * 50f);
            }

            int xfyunPitch;
            if (pitch <= 1.0f) {
                xfyunPitch = (int)Math.round((pitch - 0.5f) * 100f);
            } else {
                xfyunPitch = (int)Math.round(50f + (pitch - 1.0f) * 50f);
            }

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
                    FileOutputStream outputStream = null;
                    try {
                        outputStream = new FileOutputStream(file);
                        outputStream.write(bytes);
                        outputStream.flush();
                        
                        // 确保文件句柄被释放
                        if(outputStream != null){
                            try {
                                outputStream.close();
                                outputStream = null;  // 标记已关闭
                            } catch (IOException e) {
                                logger.error("关闭 xfyun 语音合成文件流失败", e);
                                throw new RuntimeException("文件关闭失败", e);
                            }
                        }
                        
                        // 验证文件已成功写入
                        if (!file.exists() || file.length() == 0) {
                            throw new RuntimeException("音频文件写入失败");
                        }
                        
                    } catch (Exception e) {
                        logger.error("写入音频文件失败", e);
                        throw new RuntimeException(e);
                    } finally {
                        // 最后确保countDown被调用
                        recognitionLatch.countDown();
                    }
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
        // 等待语音合成完成或超时
        boolean recognized = recognitionLatch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!recognized) {
            logger.warn("讯飞云语音合成超时");
        }
        return true;
    }

}