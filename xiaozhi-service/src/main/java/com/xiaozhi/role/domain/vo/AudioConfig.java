package com.xiaozhi.role.domain.vo;

/**
 * VAD（语音活动检测）音频配置值对象。
 */
public record AudioConfig(Float vadEnergyTh, Float vadSpeechTh,
                           Float vadSilenceTh, Integer vadSilenceMs) {

    public static AudioConfig defaults() {
        return new AudioConfig(null, null, null, null);
    }

    /** 按 patch 合并：patch 没给的字段保留当前值，与「局部更新」语义一致 */
    public AudioConfig merge(AudioConfig patch) {
        if (patch == null) {
            return this;
        }
        return new AudioConfig(
                patch.vadEnergyTh() != null ? patch.vadEnergyTh() : vadEnergyTh,
                patch.vadSpeechTh() != null ? patch.vadSpeechTh() : vadSpeechTh,
                patch.vadSilenceTh() != null ? patch.vadSilenceTh() : vadSilenceTh,
                patch.vadSilenceMs() != null ? patch.vadSilenceMs() : vadSilenceMs);
    }
}
