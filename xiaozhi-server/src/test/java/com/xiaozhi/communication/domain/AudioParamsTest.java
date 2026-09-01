package com.xiaozhi.communication.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudioParamsTest {

    private static AudioParams device(String format, int sampleRate, int channels, int frameDuration) {
        return new AudioParams()
                .setFormat(format)
                .setSampleRate(sampleRate)
                .setChannels(channels)
                .setFrameDuration(frameDuration);
    }

    @Test
    void serverCapabilityReturnsIndependentInstances() {
        AudioParams first = AudioParams.serverCapability();
        AudioParams second = AudioParams.serverCapability();

        assertThat(first).isNotSameAs(second).isEqualTo(second);

        // 改动下发给某个会话的对象不能影响其他会话
        first.setSampleRate(24000);
        assertThat(AudioParams.serverCapability().getSampleRate()).isEqualTo(16000);
    }

    @Test
    void serverCapabilityMatchesProcessingChain() {
        AudioParams params = AudioParams.serverCapability();

        assertThat(params.getFormat()).isEqualTo("opus");
        assertThat(params.getSampleRate()).isEqualTo(16000);
        assertThat(params.getChannels()).isEqualTo(1);
        assertThat(params.getFrameDuration()).isEqualTo(60);
    }

    @Test
    void noMismatchForOfficialFirmwareParams() {
        assertThat(device("opus", 16000, 1, 60).mismatchAgainstServer()).isNull();
    }

    @Test
    void undeclaredFieldsAreNotComparedAsMismatch() {
        assertThat(new AudioParams().mismatchAgainstServer()).isNull();
    }

    @Test
    void reportsFormatMismatch() {
        assertThat(device("pcm", 16000, 1, 60).mismatchAgainstServer())
                .contains("格式=pcm");
    }

    @Test
    void formatComparisonIsCaseInsensitive() {
        assertThat(device("OPUS", 16000, 1, 60).mismatchAgainstServer()).isNull();
    }

    @Test
    void reportsSampleRateMismatch() {
        assertThat(device("opus", 24000, 1, 60).mismatchAgainstServer())
                .contains("采样率=24000");
    }

    @Test
    void reportsAllMismatchesTogether() {
        String mismatch = device("pcm", 8000, 2, 20).mismatchAgainstServer();

        assertThat(mismatch).contains("格式=pcm").contains("采样率=8000")
                .contains("声道=2").contains("帧时长=20ms");
    }
}
