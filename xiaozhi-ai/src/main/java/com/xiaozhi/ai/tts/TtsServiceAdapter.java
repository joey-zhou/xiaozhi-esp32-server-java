package com.xiaozhi.ai.tts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 适配器：将项目的 {@link TtsService} / {@link StreamingTextToSpeech} 桥接到
 * Spring AI 的 {@link TextToSpeechModel} 标准接口。
 * <p>
 * 使用方式：
 * <pre>
 * TtsService ttsService = ttsServiceFactory.getTtsService(config, voiceName, pitch, speed);
 * TextToSpeechModel springAiTts = new TtsServiceAdapter(ttsService);
 * byte[] audio = springAiTts.call("你好");
 * </pre>
 * <p>
 * 这样现有的 TTS Provider 无需修改即可兼容 Spring AI 的 TTS 生态。
 */
@Slf4j
public class TtsServiceAdapter implements TextToSpeechModel {

    private final TtsService ttsService;

    public TtsServiceAdapter(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    @Override
    public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
        String text = prompt.getInstructions().getText();
        try {
            Path audioPath = ttsService.textToSpeech(text);
            byte[] audioBytes = Files.readAllBytes(audioPath);
            // 清理临时文件
            Files.deleteIfExists(audioPath);
            return new TextToSpeechResponse(List.of(new Speech(audioBytes)));
        } catch (Exception e) {
            log.error("TTS call failed for provider {}: {}", ttsService.getProviderName(), e.getMessage(), e);
            throw new RuntimeException("TTS synthesis failed", e);
        }
    }

    @Override
    public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
        throw new RuntimeException("TTS streaming failed");
    }

    @Override
    public TextToSpeechOptions getDefaultOptions() {
        return ttsService.getOptions();
    }

    /**
     * 获取底层的原始 TtsService 实例。
     */
    public TtsService unwrap() {
        return ttsService;
    }
}
