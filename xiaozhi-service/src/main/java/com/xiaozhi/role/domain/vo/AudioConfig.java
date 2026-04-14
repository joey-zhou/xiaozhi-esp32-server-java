package com.xiaozhi.role.domain.vo;

/**
 * VAD（语音活动检测）音频配置值对象。
 */
public record AudioConfig(Float vadEnergyTh, Float vadSpeechTh,
                           Float vadSilenceTh, Integer vadSilenceMs) {

    public static AudioConfig defaults() {
        return new AudioConfig(null, null, null, null);
    }
}
