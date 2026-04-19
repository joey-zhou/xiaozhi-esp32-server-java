package com.xiaozhi.ai.tts;

import lombok.Builder;
import lombok.Getter;
import org.springframework.ai.audio.tts.TextToSpeechOptions;

/**
 * TTS 参数配置对象，封装 voiceName/speed/pitch 等参数。
 * 直接实现 Spring AI 的 {@link TextToSpeechOptions} 接口，与 Spring AI TTS 生态无缝集成。
 * <p>
 * 所有 TTS Provider 在构造时接收此对象，替代原来的 4 个独立参数。
 */
@Getter
@Builder
public class XiaozhiTtsOptions implements TextToSpeechOptions {

    /**
     * 音色名称
     */
    private final String voiceName;

    /**
     * 语速 (0.5-2.0)，1.0 为默认速度
     */
    @Builder.Default
    private final Double speed = 1.0;

    /**
     * 音调 (0.5-2.0)，1.0 为默认音调
     */
    @Builder.Default
    private final Double pitch = 1.0;

    // ---- Spring AI TextToSpeechOptions 接口实现 ----

    @Override
    public String getModel() {
        return null;
    }

    @Override
    public String getVoice() {
        return voiceName;
    }

    @Override
    public String getFormat() {
        return null;
    }

    @Override
    public Double getSpeed() {
        return speed;
    }

    @SuppressWarnings("unchecked")
    @Override
    public XiaozhiTtsOptions copy() {
        return XiaozhiTtsOptions.builder()
                .voiceName(voiceName)
                .speed(speed)
                .pitch(pitch)
                .build();
    }
}
