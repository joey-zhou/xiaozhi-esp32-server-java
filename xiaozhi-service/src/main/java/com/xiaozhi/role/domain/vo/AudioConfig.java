package com.xiaozhi.role.domain.vo;

import com.xiaozhi.common.model.bo.RoleBO;

/**
 * VAD（语音活动检测）音频配置值对象。默认值定义在 {@link RoleBO}，新建与库里为 NULL 的历史行都经 {@link #withDefaults()} 补齐。
 */
public record AudioConfig(Float vadEnergyTh, Float vadSpeechTh,
                           Float vadSilenceTh, Integer vadSilenceMs) {

    public static AudioConfig defaults() {
        return new AudioConfig(null, null, null, null).withDefaults();
    }

    /** 补齐没给值的阈值 */
    public AudioConfig withDefaults() {
        return new AudioConfig(
                vadEnergyTh != null ? vadEnergyTh : RoleBO.DEFAULT_VAD_ENERGY_TH,
                vadSpeechTh != null ? vadSpeechTh : RoleBO.DEFAULT_VAD_SPEECH_TH,
                vadSilenceTh != null ? vadSilenceTh : RoleBO.DEFAULT_VAD_SILENCE_TH,
                vadSilenceMs != null ? vadSilenceMs : RoleBO.DEFAULT_VAD_SILENCE_MS);
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
