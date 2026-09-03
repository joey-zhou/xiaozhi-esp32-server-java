package com.xiaozhi.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住内嵌样本的格式：语音必须是 16kHz 单声道 16bit 小端 PCM 且真的有声音，
 * 图片必须是能被视觉模型接收的 PNG。样本坏掉时配置测试会误报成「模型不可用」。
 */
class ConfigTestSamplesTest {

    /** 16kHz 单声道 16bit：每秒 32000 字节 */
    private static final double BYTES_PER_SECOND = 32000.0;

    private static final int MAX_FRAME_BYTES = 1920;

    @Test
    void speechFramesArePcmChunksWithSilencePadding() {
        List<byte[]> frames = ConfigTestSamples.speechFrames();

        assertThat(frames).hasSizeGreaterThan(3);
        assertThat(frames).allSatisfy(frame -> {
            assertThat(frame.length).isBetween(2, MAX_FRAME_BYTES);
            assertThat(frame.length % 2).isZero();
        });
        assertThat(frames.get(0)).containsOnly((byte) 0);
        assertThat(frames.get(frames.size() - 1)).containsOnly((byte) 0);

        double seconds = totalBytes(frames) / BYTES_PER_SECOND;
        assertThat(seconds).isBetween(0.5, 2.0);
    }

    @Test
    void speechFramesCarryAudibleSpeech() {
        List<byte[]> frames = ConfigTestSamples.speechFrames();

        int peak = 0;
        for (byte[] frame : frames) {
            for (int i = 0; i + 1 < frame.length; i += 2) {
                int sample = (short) ((frame[i] & 0xFF) | (frame[i + 1] << 8));
                peak = Math.max(peak, Math.abs(sample));
            }
        }

        assertThat(peak).isBetween(16000, 32767);
    }

    @Test
    void speechFramesAreFreshCopiesPerCall() {
        List<byte[]> first = ConfigTestSamples.speechFrames();
        byte[] speechFrame = first.get(2);
        byte[] original = Arrays.copyOf(speechFrame, speechFrame.length);
        Arrays.fill(speechFrame, (byte) 0);

        List<byte[]> second = ConfigTestSamples.speechFrames();

        assertThat(second.get(2)).containsExactly(original);
    }

    @Test
    void imageSampleIsPng() {
        byte[] pngMagic = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

        byte[] image = ConfigTestSamples.imagePng();

        assertThat(image).hasSizeBetween(pngMagic.length, 4096).startsWith(pngMagic);
    }

    private static int totalBytes(List<byte[]> frames) {
        return frames.stream().mapToInt(frame -> frame.length).sum();
    }
}
