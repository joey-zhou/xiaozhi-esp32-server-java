package com.xiaozhi.ai.tts;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.config.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 按角色取合成服务只在这里判一次。
 * 绑定的配置被停用或删除后回退默认 Edge，回退时不能带着角色原来的音色名——
 * 那是别家厂商的音色，Edge 不认识就合成不出声，回退等于没回退。
 */
class TtsServiceFactoryRoleTest {

    private static final int TTS_ID = 30;

    private final ConfigService configService = mock(ConfigService.class);

    /** 建出来的实例：provider、configId、音色 */
    private final List<String> creations = new ArrayList<>();

    private TtsServiceFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TtsServiceFactory() {
            @Override
            public TtsService createApiService(ConfigBO config, String voiceName, Double pitch, Double speed) {
                creations.add(config.getProvider() + ":" + config.getConfigId() + ":" + voiceName);
                return mock(TtsService.class);
            }
        };
        ReflectionTestUtils.setField(factory, "configService", configService);
    }

    @Test
    void disabledConfigFallsBackToEdgeWithItsOwnDefaultVoice() {
        when(configService.getBO(TTS_ID)).thenReturn(new ConfigBO().setConfigId(TTS_ID).setProvider("aliyun")
            .setState(ConfigBO.STATE_DISABLED));

        assertThat(factory.getTtsService(roleWithAliyunVoice())).isSameAs(factory.getDefaultTtsService());
        assertThat(creations).containsExactly("edge:null:zh-CN-XiaoyiNeural");
    }

    @Test
    void deletedConfigAlsoFallsBackToTheDefaultVoice() {
        when(configService.getBO(TTS_ID)).thenReturn(null);

        assertThat(factory.getTtsService(roleWithAliyunVoice())).isSameAs(factory.getDefaultTtsService());
        assertThat(creations).containsExactly("edge:null:zh-CN-XiaoyiNeural");
    }

    @Test
    void enabledConfigUsesTheRolesVoice() {
        when(configService.getBO(TTS_ID)).thenReturn(new ConfigBO().setConfigId(TTS_ID).setProvider("aliyun")
            .setState(ConfigBO.STATE_ENABLED));

        factory.getTtsService(roleWithAliyunVoice());

        assertThat(creations).containsExactly("aliyun:30:longxiaochun");
    }

    /** 角色本来就选的 Edge：音色名是 Edge 自己的，照常带上，也不用查配置 */
    @Test
    void roleWithoutConfigKeepsItsEdgeVoice() {
        RoleBO role = new RoleBO();
        role.setTtsId(-1);
        role.setVoiceName("zh-CN-XiaoxiaoNeural");

        factory.getTtsService(role);

        assertThat(creations).containsExactly("edge:null:zh-CN-XiaoxiaoNeural");
        verify(configService, never()).getBO(any());
    }

    private static RoleBO roleWithAliyunVoice() {
        RoleBO role = new RoleBO();
        role.setTtsId(TTS_ID);
        role.setVoiceName("longxiaochun");
        return role;
    }
}
