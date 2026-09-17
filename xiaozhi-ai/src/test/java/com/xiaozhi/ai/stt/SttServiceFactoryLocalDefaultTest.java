package com.xiaozhi.ai.stt;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.port.ProviderTokenClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 本地识别的 provider 选择：模型目录都不在时，默认与显式指定的本地 provider 都要报清楚原因，
 * 而不是悄悄借用别的配置创建出的第三方实例。
 */
class SttServiceFactoryLocalDefaultTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultProviderFailsClearlyWhenNoLocalModelIsPresent() {
        SttServiceFactory factory = newFactory();
        factory.initializeDefaultSttService();

        assertThatThrownBy(factory::getDefaultSttService)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SenseVoice")
                .hasMessageContaining("Vosk");
    }

    @Test
    void explicitSenseVoiceProviderReportsMissingModel() {
        SttServiceFactory factory = newFactory();

        assertThatThrownBy(() -> factory.getSttService(new ConfigBO().setProvider("sherpa-onnx").setConfigId(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sherpa-onnx");
    }

    @Test
    void blankProviderFallsToLocalDefault() {
        SttServiceFactory factory = newFactory();

        assertThatThrownBy(() -> factory.getSttService(new ConfigBO().setProvider("").setConfigId(4)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("本地语音识别不可用");
    }

    @Test
    void unknownProviderIsRejected() {
        SttServiceFactory factory = newFactory();

        assertThatThrownBy(() -> factory.getSttService(new ConfigBO().setProvider("nope").setConfigId(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SttServiceFactory.SENSE_VOICE_PROVIDER).isEqualTo("sherpa-onnx");
    }

    private SttServiceFactory newFactory() {
        SttServiceFactory factory = new SttServiceFactory();
        RuntimePathConfig paths = new RuntimePathConfig();
        // 两个模型目录都指向不存在的位置
        paths.setSenseVoiceModelDir(tempDir.resolve("sense-voice").toString());
        paths.setVoskModelDir(tempDir.resolve("vosk-model").toString());
        paths.setNativeLibDir(tempDir.resolve("lib").toString());
        ReflectionTestUtils.setField(factory, "runtimePathConfig", paths);
        ReflectionTestUtils.setField(factory, "tokenClient", mock(ProviderTokenClient.class));
        return factory;
    }
}
