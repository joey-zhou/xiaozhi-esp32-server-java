package com.xiaozhi.websocket.tts.providers;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.websocket.tts.TtsService;

import cn.hutool.core.util.StrUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.Consumer;

public class AliyunTtsService implements TtsService {
    private static final Logger logger = LoggerFactory.getLogger(AliyunTtsService.class);

    private static final String PROVIDER_NAME = "aliyun";

    // 阿里云配置
    private final String apiKey;
    private final String voiceName;
    private final String outputPath;

    public AliyunTtsService(SysConfig config,
                            String voiceName, String outputPath) {
        this.apiKey = config.getApiKey();
        this.voiceName = voiceName;
        this.outputPath = outputPath;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String getAudioFileName() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid + ".mp3";
    }

    @Override
    public String textToSpeech(String text) throws Exception{
        try {
            File outputDir = new File(outputPath);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            if (voiceName.contains("sambert")) {
                return ttsSambert(text);
            } else if (getVoiceByName(voiceName) != null) {
                return ttsQwen(text);
            } else {
                return ttsCosyvoice(text);
            }
        } catch (Exception e) {
            logger.error("语音合成aliyun -使用{}模型语音合成失败：",voiceName,e);
            throw new Exception("语音合成失败");
        }
    }

    private String ttsQwen(String text) {
        try {
            AudioParameters.Voice voice = getVoiceByName(voiceName);
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .model("qwen-tts")
                    .apiKey(apiKey)
                    .text(text)
                    .voice(voice)
                    .build();
            MultiModalConversation conv = new MultiModalConversation();
            MultiModalConversationResult result = conv.call(param);
            String audioUrl = result.getOutput().getAudio().getUrl();
            String outPath = outputPath + File.separator + getAudioFileName();
            File file = new File(outPath);
            // 下载音频文件到本地
            try (InputStream in = new URL(audioUrl).openStream();
                 FileOutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return outPath;
        }catch (Exception e){
            logger.error("语音合成aliyun -使用{}模型语音合成失败：",voiceName,e);
            return StrUtil.EMPTY;
        }
    }

    private AudioParameters.Voice getVoiceByName(String voiceName) {
        switch (voiceName){
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

    private String ttsCosyvoice(String text) {
        try {
            com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam param =
            com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam.builder()
                            .apiKey(apiKey)
                            .model("cosyvoice-v1")
                            .voice(voiceName)
                            .build();
            com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer synthesizer = new com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer(param, null);
            ByteBuffer audio = synthesizer.call(text);
            String outPath = outputPath + File.separator + getAudioFileName();

            File file = new File(outPath);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(audio.array());
            } catch (IOException e) {
                logger.error("语音合成aliyun -使用{}模型语音合成失败：",voiceName,e);
                return StrUtil.EMPTY;
            }
            return outPath;
        } catch (Exception e) {
            logger.error("语音合成aliyun -使用{}模型语音合成失败：",voiceName,e);
            return StrUtil.EMPTY;
        }
    }

    public String ttsSambert(String text) {
        try {
            SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                    .apiKey(apiKey)
                    .model(voiceName)
                    .text(text)
                    .sampleRate(AudioUtils.SAMPLE_RATE)
                    .format(SpeechSynthesisAudioFormat.MP3)
                    .build();
            SpeechSynthesizer synthesizer = new SpeechSynthesizer();
            ByteBuffer audio = synthesizer.call(param);
            String outPath = outputPath + File.separator + getAudioFileName();
            File file = new File(outPath);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(audio.array());
            } catch (IOException e) {
                logger.error("语音合成aliyun - 使用{}模型失败：",voiceName,e);
                return StrUtil.EMPTY;
            }
            return outPath;
        }catch (Exception e){
            logger.error("语音合成aliyun - 使用{}模型失败：",voiceName,e);
            return StrUtil.EMPTY;
        }
    }

    @Override
    public void streamTextToSpeech(String text, Consumer<byte[]> audioDataConsumer) throws Exception {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'streamTextToSpeech'");
    }

}