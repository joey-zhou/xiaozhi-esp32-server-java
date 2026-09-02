package com.xiaozhi.ai.stt.providers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阿里云各 Paraformer 模型的采样率要求见官方文档：
 * paraformer-realtime-v2 任意采样率、v1 仅 16000Hz、8k 系列仅 8000Hz。
 */
class AliyunSttServiceSampleRateTest {

    @Test
    void eightKilohertzModelsRequireEightThousand() {
        // paraformer-realtime-8k-v2 是唯一支持情感识别的 Paraformer 模型，必须能用
        assertThat(AliyunSttService.requiredSampleRate("paraformer-realtime-8k-v2")).isEqualTo(8000);
        assertThat(AliyunSttService.requiredSampleRate("paraformer-realtime-8k-v1")).isEqualTo(8000);
        assertThat(AliyunSttService.requiredSampleRate("fun-asr-flash-8k-realtime")).isEqualTo(8000);
        assertThat(AliyunSttService.requiredSampleRate("fun-asr-flash-8k-realtime-2026-01-28")).isEqualTo(8000);
    }

    @Test
    void wideBandModelsKeepDeviceSampleRate() {
        assertThat(AliyunSttService.requiredSampleRate("paraformer-realtime-v2")).isEqualTo(16000);
        assertThat(AliyunSttService.requiredSampleRate("paraformer-realtime-v1")).isEqualTo(16000);
        assertThat(AliyunSttService.requiredSampleRate("fun-asr-realtime")).isEqualTo(16000);
    }

    @Test
    void modelNameMatchingIsCaseInsensitive() {
        assertThat(AliyunSttService.requiredSampleRate("Paraformer-Realtime-8K-V2")).isEqualTo(8000);
    }
}
