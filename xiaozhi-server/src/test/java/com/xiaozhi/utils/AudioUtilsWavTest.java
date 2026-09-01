package com.xiaozhi.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioUtilsWavTest {

    /** 构造指定采样率的单声道16bit WAV */
    private static byte[] wav(int sampleRate, int sampleCount) throws IOException {
        byte[] pcm = new byte[sampleCount * 2];
        for (int i = 0; i < sampleCount; i++) {
            short v = (short) (Math.sin(2 * Math.PI * 440 * i / sampleRate) * 8000);
            pcm[i * 2] = (byte) (v & 0xFF);
            pcm[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeBytes("RIFF");
            dos.writeInt(Integer.reverseBytes(36 + pcm.length));
            dos.writeBytes("WAVE");
            dos.writeBytes("fmt ");
            dos.writeInt(Integer.reverseBytes(16));
            dos.writeShort(Short.reverseBytes((short) 1));
            dos.writeShort(Short.reverseBytes((short) 1));
            dos.writeInt(Integer.reverseBytes(sampleRate));
            dos.writeInt(Integer.reverseBytes(sampleRate * 2));
            dos.writeShort(Short.reverseBytes((short) 2));
            dos.writeShort(Short.reverseBytes((short) 16));
            dos.writeBytes("data");
            dos.writeInt(Integer.reverseBytes(pcm.length));
            dos.write(pcm);
        }
        return baos.toByteArray();
    }

    @Test
    void keepsPcmUntouchedWhenSampleRateMatches() throws IOException {
        byte[] pcm = AudioUtils.wavToPcm(wav(16000, 1600));

        assertThat(pcm).hasSize(3200);
    }

    @Test
    void resamplesWhenWavDeclaresHigherRate() throws IOException {
        // 24k 的 2400 样本重采样到 16k 应为 1600 样本
        byte[] pcm = AudioUtils.wavToPcm(wav(24000, 2400));

        assertThat(pcm).hasSize(3200);
    }

    @Test
    void resamplesWhenWavDeclaresLowerRate() throws IOException {
        // 8k 的 800 样本升到 16k 应为 1600 样本
        byte[] pcm = AudioUtils.wavToPcm(wav(8000, 800));

        assertThat(pcm).hasSize(3200);
    }

    @Test
    void resamples22050WhichIsAliyunCosyVoiceNativeRate() throws IOException {
        byte[] pcm = AudioUtils.wavToPcm(wav(22050, 22050));

        // 22050 -> 16000，1 秒音频应得约 16000 样本
        assertThat(pcm.length / 2).isBetween(15999, 16001);
    }

    @Test
    void rejectsNonWavData() {
        assertThatThrownBy(() -> AudioUtils.wavToPcm("this is definitely not a wav file at all".getBytes()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void resampleIsNoopForEqualRates() {
        byte[] pcm = {1, 2, 3, 4};

        assertThat(AudioUtils.resamplePcm(pcm, 16000, 16000)).isSameAs(pcm);
    }
}
