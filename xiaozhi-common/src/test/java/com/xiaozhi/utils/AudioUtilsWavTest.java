package com.xiaozhi.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 钉住 WAV 解析入口的两条硬约束：任何采样率的 WAV 取出的 PCM 一律归一到服务端 16k，
 * 采样率本来就一致时必须原样返回不做插值；头部非法时按具体原因抛 IOException，不能吞成通用错误。
 */
class AudioUtilsWavTest {

    /** 生成单声道16bit小端PCM */
    private static byte[] pcm(int sampleRate, int sampleCount) {
        byte[] pcm = new byte[sampleCount * 2];
        for (int i = 0; i < sampleCount; i++) {
            short v = (short) (Math.sin(2 * Math.PI * 440 * i / sampleRate) * 8000);
            pcm[i * 2] = (byte) (v & 0xFF);
            pcm[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return pcm;
    }

    /** 用给定PCM构造指定采样率的单声道16bit WAV */
    private static byte[] wav(int sampleRate, byte[] pcm) throws IOException {
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

    private static byte[] wav(int sampleRate, int sampleCount) throws IOException {
        return wav(sampleRate, pcm(sampleRate, sampleCount));
    }

    @Test
    void keepsPcmUntouchedWhenSampleRateMatches() throws IOException {
        byte[] pcm = pcm(16000, 1600);

        assertThat(AudioUtils.wavToPcm(wav(16000, pcm))).isEqualTo(pcm);
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
    void rejectsDataShorterThanWavHeader() {
        // 不足 44 字节，连头都读不全
        assertThatThrownBy(() -> AudioUtils
                .wavToPcm("this is definitely not a wav file at all".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IOException.class)
                .hasMessage("无效的WAV数据");
    }

    @Test
    void rejectsDataWithWrongRiffMagic() throws IOException {
        byte[] wav = wav(16000, 1600);
        wav[0] = 'X';

        assertThatThrownBy(() -> AudioUtils.wavToPcm(wav))
                .isInstanceOf(IOException.class)
                .hasMessage("不是有效的WAV文件格式");
    }

    @Test
    void rejectsWavWithoutDataChunk() throws IOException {
        // 前 36 字节是 RIFF/WAVE 与 fmt 子块，36 起本应是 data 标记，改掉后全文再无 data 子块
        byte[] wav = Arrays.copyOf(wav(16000, 1600), 44);
        wav[36] = 'L';
        wav[37] = 'I';
        wav[38] = 'S';
        wav[39] = 'T';

        assertThatThrownBy(() -> AudioUtils.wavToPcm(wav))
                .isInstanceOf(IOException.class)
                .hasMessage("在WAV文件中找不到data子块");
    }

    @Test
    void resampleIsNoopForEqualRates() {
        byte[] pcm = {1, 2, 3, 4};

        assertThat(AudioUtils.resamplePcm(pcm, 16000, 16000)).isSameAs(pcm);
    }
}
