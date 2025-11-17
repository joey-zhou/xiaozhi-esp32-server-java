package com.xiaozhi.dialogue.tts.providers;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.AudioUtils;

import cn.hutool.core.util.StrUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AliyunTtsService implements TtsService {
    private static final Logger logger = LoggerFactory.getLogger(AliyunTtsService.class);

    private static final String PROVIDER_NAME = "aliyun";
    // 添加重试次数常量
    private static final int MAX_RETRY_ATTEMPTS = 3;
    // 添加重试间隔常量（毫秒）
    private static final long RETRY_DELAY_MS = 1000;
    // 添加TTS操作超时时间（秒）
    private static final long TTS_TIMEOUT_SECONDS = 5;
    
    // 使用共享的线程池，避免频繁创建和销毁
    private static final ExecutorService sharedExecutor = Executors.newCachedThreadPool();

    // 阿里云配置
    private final String apiKey;
    private final String voiceName;
    private final String outputPath;
    
    // 语音参数
    private final Float pitch;
    private final Float speed;

    public AliyunTtsService(SysConfig config,
            String voiceName, Float pitch, Float speed, String outputPath) {
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
    public String textToSpeech(String text) throws Exception {
        try {
            if (voiceName.contains("sambert")) {
                return ttsSambert(text);
            } else if (getVoiceByName(voiceName) != null) {
                return ttsQwen(text);
            } else {
                return ttsCosyvoice(text);
            }
        } catch (Exception e) {
            logger.error("语音合成aliyun -使用{}模型语音合成失败：", voiceName, e);
            throw new Exception("语音合成失败");
        }
    }

    private String ttsQwen(String text) {
        int attempts = 0;
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                AudioParameters.Voice voice = getVoiceByName(voiceName);
                MultiModalConversationParam param = MultiModalConversationParam.builder()
                        .model("qwen-tts")
                        .apiKey(apiKey)
                        .text(text)
                        .voice(voice)
                        .build();
                
                // 使用共享线程池而不是每次创建新的
                Future<MultiModalConversationResult> future = sharedExecutor.submit(() -> {
                    MultiModalConversation conv = new MultiModalConversation();
                    return conv.call(param);
                });
                
                // 等待结果，设置超时
                MultiModalConversationResult result;
                try {
                    result = future.get(TTS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    logger.warn("语音合成aliyun - 使用{}模型超时，正在重试 ({}/{})", voiceName, attempts + 1, MAX_RETRY_ATTEMPTS);
                    attempts++;
                    if (attempts >= MAX_RETRY_ATTEMPTS) {
                        logger.error("语音合成aliyun - 使用{}模型多次超时，放弃重试", voiceName);
                        return StrUtil.EMPTY;
                    }
                    // 等待一段时间后重试
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    continue;
                }
                
                // 检查结果是否有效
                if (result == null || result.getOutput() == null || 
                    result.getOutput().getAudio() == null || 
                    result.getOutput().getAudio().getUrl() == null) {
                    
                    logger.warn("语音合成aliyun - 使用{}模型返回无效结果，正在重试 ({}/{})", voiceName, attempts + 1, MAX_RETRY_ATTEMPTS);
                    attempts++;
                    if (attempts >= MAX_RETRY_ATTEMPTS) {
                        logger.error("语音合成aliyun - 使用{}模型多次返回无效结果，放弃重试", voiceName);
                        return StrUtil.EMPTY;
                    }
                    // 等待一段时间后重试
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    continue;
                }
                
                String audioUrl = result.getOutput().getAudio().getUrl();
                String outPath = outputPath + getAudioFileName();
                File file = new File(outPath);
                
                // 下载音频文件到本地，也使用共享线程池
                Future<Boolean> downloadFuture = sharedExecutor.submit(() -> {
                    try (InputStream in = new URL(audioUrl).openStream();
                            FileOutputStream out = new FileOutputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                
                try {
                    Boolean downloadSuccess = downloadFuture.get(TTS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (!downloadSuccess) {
                        throw new IOException("下载音频文件失败");
                    }
                } catch (TimeoutException e) {
                    downloadFuture.cancel(true);
                    logger.warn("语音合成aliyun - 使用{}模型下载音频超时，正在重试 ({}/{})", voiceName, attempts + 1, MAX_RETRY_ATTEMPTS);
                    attempts++;
                    if (attempts >= MAX_RETRY_ATTEMPTS) {
                        logger.error("语音合成aliyun - 使用{}模型多次下载超时，放弃重试", voiceName);
                        return StrUtil.EMPTY;
                    }
                    // 等待一段时间后重试
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    continue;
                }
                
                return outPath;
            } catch (Exception e) {
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    logger.warn("语音合成aliyun - 使用{}模型失败，正在重试 ({}/{}): {}", voiceName, attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        // 等待一段时间后重试
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("重试等待被中断", ie);
                        return StrUtil.EMPTY;
                    }
                } else {
                    logger.error("语音合成aliyun - 使用{}模型语音合成失败，已达到最大重试次数：", voiceName, e);
                    return StrUtil.EMPTY;
                }
            }
        }
        return StrUtil.EMPTY;
    }

    private AudioParameters.Voice getVoiceByName(String voiceName) {
        switch (voiceName) {
            case "Chelsie":
                return AudioParameters.Voice.CHELSIE;
            case "Cherry":
                return AudioParameters.Voice.CHERRY;
            case "Ethan":
                return AudioParameters.Voice.ETHAN;
            case "Serena":
                return AudioParameters.Voice.SERENA;
            default:
                return null;
        }
    }

    // cosyvoice默认并发只有3个，所以需要增加一个重试机制
    private String ttsCosyvoice(String text) {
        int attempts = 0;
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam param =
                com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam.builder()
                                .apiKey(apiKey)
                                .model("cosyvoice-v2")
                                .voice(voiceName)
                                .speechRate(speed)
                                .pitchRate(pitch)
                                .format(com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat.WAV_16000HZ_MONO_16BIT)
                                .build();
                
                // 使用共享线程池
                Future<ByteBuffer> future = sharedExecutor.submit(() -> {
                    com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer synthesizer = 
                        new com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer(param, null);
                    return synthesizer.call(text);
                });
                
                // 等待结果，设置超时
                ByteBuffer audio;
                try {
                    audio = future.get(TTS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    logger.warn("语音合成aliyun - 使用{}模型超时，正在重试 ({}/{})", voiceName, attempts + 1, MAX_RETRY_ATTEMPTS);
                    attempts++;
                    if (attempts >= MAX_RETRY_ATTEMPTS) {
                        logger.error("语音合成aliyun - 使用{}模型多次超时，放弃重试", voiceName);
                        return StrUtil.EMPTY;
                    }
                    // 等待一段时间后重试
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    continue;
                }
                
                // 检查返回的ByteBuffer是否为null
                if (audio == null) {
                    attempts++;
                    if (attempts < MAX_RETRY_ATTEMPTS) {
                        logger.warn("语音合成aliyun - 使用{}模型返回null，正在重试 ({}/{})", voiceName, attempts, MAX_RETRY_ATTEMPTS);
                        // 等待一段时间后重试
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                        continue;
                    } else {
                        logger.error("语音合成aliyun - 使用{}模型多次返回null，放弃重试", voiceName);
                        return StrUtil.EMPTY;
                    }
                }
                
                String outPath = outputPath + getAudioFileName();
                File file = new File(outPath);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(audio.array());
                } catch (IOException e) {
                    logger.error("语音合成aliyun -使用{}模型语音合成失败：", voiceName, e);
                    return StrUtil.EMPTY;
                }
                return outPath;
            } catch (Exception e) {
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    logger.warn("语音合成aliyun - 使用{}模型失败，正在重试 ({}/{}): {}", voiceName, attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        // 等待一段时间后重试
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("重试等待被中断", ie);
                        return StrUtil.EMPTY;
                    }
                } else {
                    logger.error("语音合成aliyun -使用{}模型语音合成失败，已达到最大重试次数：", voiceName, e);
                    return StrUtil.EMPTY;
                }
            }
        }
        return StrUtil.EMPTY;
    }

    public String ttsSambert(String text) {
        int attempts = 0;
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                        .apiKey(apiKey)
                        .model(voiceName)
                        .text(text)
                        .rate(speed)
                        .pitch(pitch)
                        .sampleRate(AudioUtils.SAMPLE_RATE)
                        .format(SpeechSynthesisAudioFormat.WAV)
                        .build();
                
                // 使用共享线程池
                Future<ByteBuffer> future = sharedExecutor.submit(() -> {
                    SpeechSynthesizer synthesizer = new SpeechSynthesizer();
                    return synthesizer.call(param);
                });
                
                // 等待结果，设置超时
                ByteBuffer audio;
                try {
                    audio = future.get(TTS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    logger.warn("语音合成aliyun - 使用{}模型超时，正在重试 ({}/{})，文本：{}", voiceName, attempts + 1, MAX_RETRY_ATTEMPTS, text);
                    attempts++;
                    if (attempts >= MAX_RETRY_ATTEMPTS) {
                        logger.error("语音合成aliyun - 使用{}模型多次超时，放弃重试，文本：{}", voiceName, text);
                        return StrUtil.EMPTY;
                    }
                    // 等待一段时间后重试
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    continue;
                }
                
                // 检查返回的ByteBuffer是否为null
                if (audio == null) {
                    attempts++;
                    if (attempts < MAX_RETRY_ATTEMPTS) {
                        logger.warn("语音合成aliyun - 使用{}模型返回null，正在重试 ({}/{})", voiceName, attempts, MAX_RETRY_ATTEMPTS);
                        // 等待一段时间后重试
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                        continue;
                    } else {
                        logger.error("语音合成aliyun - 使用{}模型多次返回null，放弃重试", voiceName);
                        return StrUtil.EMPTY;
                    }
                }
                
                String outPath = outputPath + getAudioFileName();
                File file = new File(outPath);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(audio.array());
                } catch (IOException e) {
                    logger.error("语音合成aliyun - 使用{}模型失败：", voiceName, e);
                    return StrUtil.EMPTY;
                }
                return outPath;
            } catch (Exception e) {
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    logger.warn("语音合成aliyun - 使用{}模型失败，正在重试 ({}/{}): {}", voiceName, attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        // 等待一段时间后重试
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("重试等待被中断", ie);
                        return StrUtil.EMPTY;
                    }
                } else {
                    logger.error("语音合成aliyun - 使用{}模型失败，已达到最大重试次数：", voiceName, e);
                    return StrUtil.EMPTY;
                }
            }
        }
        return StrUtil.EMPTY;
    }

}