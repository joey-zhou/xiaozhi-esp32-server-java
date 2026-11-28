package com.xiaozhi.role;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.req.TestVoiceReq;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/** 试听：本地免配置的合成直接用，其余要有自己能用的语音配置；合成结果存进对象存储给前端播 */
@ExtendWith(MockitoExtension.class)
class RoleSpeechAppServiceTest {

    @Mock
    private ConfigService configService;
    @Mock
    private TtsServiceFactory ttsServiceFactory;
    @Mock
    private StorageServiceFactory storageServiceFactory;
    @Mock
    private TtsService ttsService;
    @Mock
    private StorageService storageService;

    @Spy
    private RuntimePathConfig runtimePathConfig = new RuntimePathConfig();

    @InjectMocks
    private RoleSpeechAppService roleSpeechAppService;

    private static TestVoiceReq req(String provider, Integer ttsId) {
        TestVoiceReq req = new TestVoiceReq();
        req.setMessage("你好");
        req.setProvider(provider);
        req.setTtsId(ttsId);
        req.setVoiceName("xiaoxiao");
        return req;
    }

    /** 合成文件落在 data-dir 下的绝对路径，存储键仍是相对的 */
    @Test
    void theDefaultProviderNeedsNoConfig() throws Exception {
        Path synthesized = runtimePathConfig.resolveAudioDir().resolve("a.mp3");
        when(ttsServiceFactory.getTtsService(isNull(), anyString(), any(), any())).thenReturn(ttsService);
        when(ttsService.textToSpeech("你好")).thenReturn(synthesized);
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(storageService.upload(synthesized, "audio/a.mp3")).thenReturn("audio/a.mp3");

        assertThat(roleSpeechAppService.preview(req(TtsServiceFactory.DEFAULT_PROVIDER, null)).getAudioUrl())
                .isEqualTo("audio/a.mp3");
    }

    @Test
    void otherProvidersNeedAConfig() {
        assertThatThrownBy(() -> roleSpeechAppService.preview(req("aliyun", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> roleSpeechAppService.preview(req("aliyun", 5)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void noAudioIsAFailure() throws Exception {
        ConfigBO config = new ConfigBO().setProvider("aliyun");
        when(configService.getBO(5)).thenReturn(config);
        when(ttsServiceFactory.getTtsService(any(ConfigBO.class), anyString(), any(), any())).thenReturn(ttsService);
        when(ttsService.textToSpeech("你好")).thenReturn(null);

        assertThatThrownBy(() -> roleSpeechAppService.preview(req("aliyun", 5)))
                .isInstanceOf(OperationFailedException.class);
    }
}
