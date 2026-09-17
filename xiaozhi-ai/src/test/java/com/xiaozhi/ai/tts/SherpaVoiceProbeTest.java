package com.xiaozhi.ai.tts;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.model.resp.SherpaVoiceResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 钉住 Kokoro voices.bin 按文件大小推算 speaker 数量（而不是把二进制张量当 NUL 分隔字符串解析），
 * 以及 listVoices() 在 TTL 内复用同一份扫描结果。
 * <p>
 * voices.bin 由 sherpa-onnx 的 generate_voices_bin.py 生成：各说话人 float32 张量顺序拼接，
 * v1.0 起每人 (510, 1, 256)，v0.19 每人 (511, 1, 256)。官方 kokoro-multi-lang-v1_0 的 voices.bin
 * 为 28200960 字节，正好 54 × 510 × 256 × 4。
 */
class SherpaVoiceProbeTest {

    private static final int V1_FRAMES = 510;
    private static final int V019_FRAMES = 511;

    @TempDir
    Path tempDir;

    private SherpaVoiceProbe newProbe(Path ttsModelsDir) throws Exception {
        SherpaVoiceProbe probe = new SherpaVoiceProbe();
        RuntimePathConfig runtimePathConfig = mock(RuntimePathConfig.class);
        when(runtimePathConfig.resolveTtsModelsDir()).thenReturn(ttsModelsDir);
        Field field = SherpaVoiceProbe.class.getDeclaredField("runtimePathConfig");
        field.setAccessible(true);
        field.set(probe, runtimePathConfig);
        return probe;
    }

    @Test
    void kokoroV1SpeakerCountIsDerivedFromVoicesBinSize() throws Exception {
        Path modelDir = Files.createDirectory(tempDir.resolve("kokoro-multi-lang-v1_0"));
        writeVoicesBin(modelDir, 54, V1_FRAMES);

        List<SherpaVoiceResp> voices = newProbe(tempDir).listVoices();

        assertThat(voices).hasSize(54);
        assertThat(voices).allMatch(v -> v.getLabel().matches("Speaker-\\d+"));
        assertThat(voices.get(53).getValue()).isEqualTo("kokoro-multi-lang-v1_0:kokoro:53");
    }

    @Test
    void kokoroV019SpeakerCountUses511FramesPerSpeaker() throws Exception {
        Path modelDir = Files.createDirectory(tempDir.resolve("kokoro-en-v0_19"));
        writeVoicesBin(modelDir, 11, V019_FRAMES);

        List<SherpaVoiceResp> voices = newProbe(tempDir).listVoices();

        assertThat(voices).hasSize(11);
    }

    @Test
    void kokoroListsOnlySpeakerZeroWhenSizeMatchesNoKnownLayout() throws Exception {
        Path modelDir = Files.createDirectory(tempDir.resolve("kokoro-broken"));
        Files.write(modelDir.resolve("voices.bin"), new byte[]{1, 2, 3, 0, 4, 5});

        List<SherpaVoiceResp> voices = newProbe(tempDir).listVoices();

        assertThat(voices).extracting(SherpaVoiceResp::getLabel).containsExactly("Speaker-0");
    }

    @Test
    void listVoicesCachesResultWithinTtl() throws Exception {
        Path modelDir = Files.createDirectory(tempDir.resolve("kokoro-multi-lang-v1_1"));
        writeVoicesBin(modelDir, 2, V1_FRAMES);

        SherpaVoiceProbe probe = newProbe(tempDir);
        List<SherpaVoiceResp> first = probe.listVoices();
        List<SherpaVoiceResp> second = probe.listVoices();

        assertThat(second).isSameAs(first);
    }

    private void writeVoicesBin(Path modelDir, int speakerCount, int frames) throws IOException {
        byte[] data = new byte[speakerCount * frames * 256 * 4];
        Files.write(modelDir.resolve("voices.bin"), data);
    }
}
