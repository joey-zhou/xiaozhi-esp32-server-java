package com.xiaozhi.role.domain.vo;

/**
 * 语音合成 / 识别配置值对象。
 * <p>音调与语速的默认值由本值对象持有：新建角色没填、库里该列为 NULL 的历史行，两条路径都经
 * {@link #withDefaults()} 补齐，取到的是同一份默认。
 */
public record VoiceConfig(Integer ttsId, Integer sttId, String voiceName,
                           Double ttsPitch, Double ttsSpeed) {

    /** 语音音调默认值 */
    public static final Double DEFAULT_TTS_PITCH = 1.0;

    /** 语音语速默认值 */
    public static final Double DEFAULT_TTS_SPEED = 1.0;

    public VoiceConfig {
        if (ttsId != null && ttsId <= 0) ttsId = null;
        if (sttId != null && sttId <= 0) sttId = null;
    }

    public static VoiceConfig defaults() {
        return new VoiceConfig(null, null, null, DEFAULT_TTS_PITCH, DEFAULT_TTS_SPEED);
    }

    /** 补齐没给值的音调与语速 */
    public VoiceConfig withDefaults() {
        return new VoiceConfig(ttsId, sttId, voiceName,
                ttsPitch != null ? ttsPitch : DEFAULT_TTS_PITCH,
                ttsSpeed != null ? ttsSpeed : DEFAULT_TTS_SPEED);
    }

    /** 按 patch 合并：patch 没给的字段保留当前值，与「局部更新」语义一致 */
    public VoiceConfig merge(VoiceConfig patch) {
        if (patch == null) {
            return this;
        }
        return new VoiceConfig(
                patch.ttsId() != null ? patch.ttsId() : ttsId,
                patch.sttId() != null ? patch.sttId() : sttId,
                patch.voiceName() != null ? patch.voiceName() : voiceName,
                patch.ttsPitch() != null ? patch.ttsPitch() : ttsPitch,
                patch.ttsSpeed() != null ? patch.ttsSpeed() : ttsSpeed);
    }
}
