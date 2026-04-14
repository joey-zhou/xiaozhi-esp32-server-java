package com.xiaozhi.ai.tts.providers;

import io.github.whitemagic2014.tts.TTS;
import io.github.whitemagic2014.tts.TTSVoice;
import io.github.whitemagic2014.tts.bean.Voice;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.XiaozhiTtsOptions;

public class EdgeTtsService implements TtsService {
    private static final Logger logger = LoggerFactory.getLogger(EdgeTtsService.class);

    private static final String PROVIDER_NAME = "edge";

    private final XiaozhiTtsOptions options;
    private final String outputPath;

    public EdgeTtsService(String voiceName, Float pitch, Float speed, String outputPath) {
        this.options = XiaozhiTtsOptions.builder().voiceName(voiceName).pitch(pitch).speed(speed).build();
        this.outputPath = outputPath;
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
            throw new Exception("文本内容为空");
        }

        Voice voiceObj = TTSVoice.provides().stream()
                .filter(v -> v.getShortName().equals(getVoiceName()))
                .findFirst()
                .orElseThrow(() -> new Exception("Edge TTS 找不到语音: " + getVoiceName()));

        int ratePercent = (int) ((getSpeed() - 1.0f) * 100);
        int pitchHz = (int) ((getPitch() - 1.0f) * 50);
        String pitch = (pitchHz >= 0 ? "+" : "") + pitchHz + "Hz";
        String rate = (ratePercent >= 0 ? "+" : "") + ratePercent + "%";

        String filename = new TTS(voiceObj, text)
                .findHeadHook()
                .isRateLimited(true)
                .storage(outputPath)
                .voicePitch(pitch)
                .voiceRate(rate)
                .connectTimeout(30000)
                .trans();

        if (filename == null || filename.isEmpty()) {
            throw new Exception("Edge TTS 合成结果为空");
        }
        return Paths.get(outputPath, filename);
    }
}