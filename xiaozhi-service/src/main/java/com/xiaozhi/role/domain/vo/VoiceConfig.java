package com.xiaozhi.role.domain.vo;

/**
 * 语音合成 / 识别配置值对象。
 */
public record VoiceConfig(Integer ttsId, Integer sttId, String voiceName,
                           Float ttsPitch, Float ttsSpeed) {

    public static VoiceConfig defaults() {
        return new VoiceConfig(null, null, null, 1.0f, 1.0f);
    }
}
