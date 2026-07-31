package com.xiaozhi.ai.tts.providers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VolcengineTtsServiceTest {

    /**
     * v3 的 speech_rate 取值范围 [-50, 100]，其中 0 为原速、100 为 2 倍速、-50 为 0.5 倍速。
     * 若误把倍率原样传入（1.0 → 1），实际只有 1% 加速，听感几乎无差别而难以察觉，故以用例钉死。
     */
    @Test
    void toV3RateConvertsRatioAnchors() {
        assertAll(
                () -> assertEquals(0, VolcengineTtsService.toV3Rate(1.0), "原速应为 0"),
                () -> assertEquals(100, VolcengineTtsService.toV3Rate(2.0), "2 倍速应为 100"),
                () -> assertEquals(-50, VolcengineTtsService.toV3Rate(0.5), "0.5 倍速应为 -50"),
                () -> assertEquals(50, VolcengineTtsService.toV3Rate(1.5)),
                () -> assertEquals(-25, VolcengineTtsService.toV3Rate(0.75)));
    }

    @Test
    void toV3RateClampsOutOfRangeValues() {
        assertAll(
                () -> assertEquals(100, VolcengineTtsService.toV3Rate(5.0), "超上限应截断为 100"),
                () -> assertEquals(-50, VolcengineTtsService.toV3Rate(0.1), "超下限应截断为 -50"),
                () -> assertEquals(0, VolcengineTtsService.toV3Rate(null), "未设置时按原速"));
    }

    /**
     * 音高是对数关系：semitone = 12 * log2(ratio)，取值范围 [-12, 12]。
     */
    @Test
    void toV3PitchConvertsRatioAnchors() {
        assertAll(
                () -> assertEquals(0, VolcengineTtsService.toV3Pitch(1.0), "原调应为 0"),
                () -> assertEquals(12, VolcengineTtsService.toV3Pitch(2.0), "2 倍频应为 +12 半音"),
                () -> assertEquals(-12, VolcengineTtsService.toV3Pitch(0.5), "0.5 倍频应为 -12 半音"));
    }

    @Test
    void toV3PitchClampsAndHandlesInvalidValues() {
        assertAll(
                () -> assertEquals(12, VolcengineTtsService.toV3Pitch(8.0), "超上限应截断为 12"),
                () -> assertEquals(-12, VolcengineTtsService.toV3Pitch(0.05), "超下限应截断为 -12"),
                () -> assertEquals(0, VolcengineTtsService.toV3Pitch(null), "未设置时按原调"),
                () -> assertEquals(0, VolcengineTtsService.toV3Pitch(0.0), "非法值按原调"),
                () -> assertEquals(0, VolcengineTtsService.toV3Pitch(-1.0), "非法值按原调"));
    }
}
