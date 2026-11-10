package com.xiaozhi.dialogue.llm.factory;

import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.config.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色绑定的 TTS 配置被停用或删除后要回退默认 Edge。回退时不能带着角色原来的音色名——
 * 那是别家厂商的音色，Edge 不认识就合成不出声，设备整轮静默，回退等于没回退。
 */
@ExtendWith(MockitoExtension.class)
class PersonaFactoryTtsTest {

    private static final int TTS_ID = 30;

    @Mock
    private ConfigService configService;

    @Mock
    private TtsServiceFactory ttsFactory;

    @Mock
    private TtsService boundTts;

    @Mock
    private TtsService defaultTts;

    private PersonaFactory personaFactory;

    @BeforeEach
    void setUp() {
        personaFactory = new PersonaFactory();
        ReflectionTestUtils.setField(personaFactory, "configService", configService);
        ReflectionTestUtils.setField(personaFactory, "ttsFactory", ttsFactory);
    }

    @Test
    void disabledTtsConfigFallsBackToEdgeWithItsOwnDefaultVoice() {
        when(configService.getBO(TTS_ID)).thenReturn(new ConfigBO().setConfigId(TTS_ID).setProvider("aliyun")
            .setState(ConfigBO.STATE_DISABLED));
        when(ttsFactory.getDefaultTtsService()).thenReturn(defaultTts);

        assertThat(personaFactory.resolveTtsService(roleWithAliyunVoice())).isSameAs(defaultTts);
        verify(ttsFactory, never()).getTtsService(any(), eq("longxiaochun"), any(), any());
    }

    @Test
    void deletedTtsConfigAlsoFallsBackToTheDefaultVoice() {
        when(configService.getBO(TTS_ID)).thenReturn(null);
        when(ttsFactory.getDefaultTtsService()).thenReturn(defaultTts);

        assertThat(personaFactory.resolveTtsService(roleWithAliyunVoice())).isSameAs(defaultTts);
    }

    @Test
    void enabledTtsConfigUsesTheRolesVoice() {
        ConfigBO config = new ConfigBO().setConfigId(TTS_ID).setProvider("aliyun").setState(ConfigBO.STATE_ENABLED);
        when(configService.getBO(TTS_ID)).thenReturn(config);
        when(ttsFactory.getTtsService(eq(config), eq("longxiaochun"), any(), any())).thenReturn(boundTts);

        assertThat(personaFactory.resolveTtsService(roleWithAliyunVoice())).isSameAs(boundTts);
    }

    /** 角色本来就选的 Edge：音色名是 Edge 自己的，照常带上 */
    @Test
    void roleWithoutTtsConfigKeepsItsEdgeVoice() {
        RoleBO role = new RoleBO();
        role.setTtsId(-1);
        role.setVoiceName("zh-CN-XiaoxiaoNeural");
        when(ttsFactory.getTtsService(isNull(), eq("zh-CN-XiaoxiaoNeural"), any(), any())).thenReturn(defaultTts);

        assertThat(personaFactory.resolveTtsService(role)).isSameAs(defaultTts);
        verify(ttsFactory, never()).getDefaultTtsService();
        verify(configService, never()).getBO(any());
    }

    private static RoleBO roleWithAliyunVoice() {
        RoleBO role = new RoleBO();
        role.setTtsId(TTS_ID);
        role.setVoiceName("longxiaochun");
        return role;
    }
}
