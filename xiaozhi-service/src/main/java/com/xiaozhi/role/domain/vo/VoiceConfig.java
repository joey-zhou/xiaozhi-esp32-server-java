package com.xiaozhi.role.domain.vo;

/**
 * 语音合成 / 识别配置值对象。
 */
public record VoiceConfig(Integer ttsId, Integer sttId, String voiceName,
                           Double ttsPitch, Double ttsSpeed) {

    public static VoiceConfig defaults() {
        return new VoiceConfig(null, null, null, 1.0, 1.0);
    }
}
