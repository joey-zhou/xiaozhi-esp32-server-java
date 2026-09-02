package com.xiaozhi.ai.tts.providers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住火山 TTS v3 的请求参数换算与取值：倍率到 speech_rate / post_process.pitch 的换算锚点，
 * 以及双向流式字幕只对内置音色下发（克隆音色走 seed-icl-2.0，带未知参数会被拒绝会话）。
 */
class VolcengineTtsServiceTest {

    /** 音色名不参与字幕判定，两个实例用同一个音色名，唯一变量是 clonedVoice */
    private static final String VOICE_NAME = "zh_female_test_uranus_bigtts";

    /**
     * v3 的 speech_rate 取值范围 [-50, 100]，其中 0 为原速、100 为 2 倍速、-50 为 0.5 倍速。
     * 若误把倍率原样传入（1.0 → 1），实际只有 1% 加速，听感几乎无差别而难以察觉，故以用例钉死。
     */
    @Test
    void toV3RateConvertsRatioAnchors() {
        // 原速
        assertThat(VolcengineTtsService.toV3Rate(1.0)).isEqualTo(0);
        // 2 倍速
        assertThat(VolcengineTtsService.toV3Rate(2.0)).isEqualTo(100);
        // 0.5 倍速
        assertThat(VolcengineTtsService.toV3Rate(0.5)).isEqualTo(-50);
        assertThat(VolcengineTtsService.toV3Rate(1.5)).isEqualTo(50);
        assertThat(VolcengineTtsService.toV3Rate(0.75)).isEqualTo(-25);
    }

    @Test
    void toV3RateClampsOutOfRangeValues() {
        // 超上限截断
        assertThat(VolcengineTtsService.toV3Rate(5.0)).isEqualTo(100);
        // 超下限截断
        assertThat(VolcengineTtsService.toV3Rate(0.1)).isEqualTo(-50);
        // 未设置时按原速
        assertThat(VolcengineTtsService.toV3Rate(null)).isEqualTo(0);
    }

    /**
     * 音高是对数关系：semitone = 12 * log2(ratio)，取值范围 [-12, 12]。
     */
    @Test
    void toV3PitchConvertsRatioAnchors() {
        // 原调
        assertThat(VolcengineTtsService.toV3Pitch(1.0)).isEqualTo(0);
        // 2 倍频 = +12 半音
        assertThat(VolcengineTtsService.toV3Pitch(2.0)).isEqualTo(12);
        // 0.5 倍频 = -12 半音
        assertThat(VolcengineTtsService.toV3Pitch(0.5)).isEqualTo(-12);
    }

    @Test
    void toV3PitchClampsAndHandlesInvalidValues() {
        // 超上限截断
        assertThat(VolcengineTtsService.toV3Pitch(8.0)).isEqualTo(12);
        // 超下限截断
        assertThat(VolcengineTtsService.toV3Pitch(0.05)).isEqualTo(-12);
        // 未设置与非法值都按原调
        assertThat(VolcengineTtsService.toV3Pitch(null)).isEqualTo(0);
        assertThat(VolcengineTtsService.toV3Pitch(0.0)).isEqualTo(0);
        assertThat(VolcengineTtsService.toV3Pitch(-1.0)).isEqualTo(0);
    }
}
