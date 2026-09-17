package com.xiaozhi.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AudioUtilsTest {

    @Test
    void pcm16ToFloatsConvertsZeros() {
        byte[] pcmData = {0, 0, 0, 0};
        float[] floats = AudioUtils.pcm16ToFloats(pcmData);

        assertThat(floats).hasSize(2);
        assertThat(floats[0]).isEqualTo(0.0f);
        assertThat(floats[1]).isEqualTo(0.0f);
    }

    @Test
    void pcm16ToFloatsConvertsNegativeOne() {
        // 0x8000 (little-endian: 0x00, 0x80) = -32768 in 16-bit signed = -1.0 in normalized float
        byte[] pcmData = {0x00, (byte) 0x80};
        float[] floats = AudioUtils.pcm16ToFloats(pcmData);

        assertThat(floats).hasSize(1);
        assertThat(floats[0]).isCloseTo(-1.0f, within(0.000015f));
    }

    @Test
    void pcm16ToFloatsConvertsPositiveValues() {
        // 0x7FFF (little-endian: 0xFF, 0x7F) = 32767 in 16-bit signed = 32767 / 32768 in normalized float
        byte[] pcmData = {(byte) 0xFF, 0x7F};
        float[] floats = AudioUtils.pcm16ToFloats(pcmData);

        assertThat(floats).hasSize(1);
        assertThat(floats[0]).isCloseTo(32767.0f / 32768.0f, within(0.000001f));
    }

    @Test
    void pcm16ToFloatsLittleEndianOrder() {
        // 0x0100 (little-endian: 0x00, 0x01) = 256 in 16-bit signed
        byte[] pcmData = {0x00, 0x01};
        float[] floats = AudioUtils.pcm16ToFloats(pcmData);

        assertThat(floats).hasSize(1);
        // 256 / 32768.0 ≈ 0.0078125
        assertThat(floats[0]).isCloseTo(256.0f / 32768.0f, within(0.000001f));
    }
}
