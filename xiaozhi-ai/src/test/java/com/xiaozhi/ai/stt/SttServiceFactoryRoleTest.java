package com.xiaozhi.ai.stt;

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
 * 按角色取识别服务只在这里判一次。绑定的配置停用或删除时回退默认识别，
 * 与模型、合成一致；默认识别也建不起来才返回 null，由对话按「听不清」处理。
 */
class SttServiceFactoryRoleTest {

    private static final int STT_ID = 40;

    private final ConfigService configService = mock(ConfigService.class);

    /** 建出来的实例：provider:configId */
    private final List<String> creations = new ArrayList<>();

    private boolean localModelMissing;

    private SttServiceFactory factory;

    @BeforeEach
    void setUp() {
        factory = new SttServiceFactory() {
            @Override
            public SttService createApiService(ConfigBO config) {
                if (localModelMissing && config.getConfigId() == -1) {
                    throw new IllegalStateException("本地语音识别不可用");
                }
                creations.add(config.getProvider() + ":" + config.getConfigId());
                return mock(SttService.class);
            }
        };
        ReflectionTestUtils.setField(factory, "configService", configService);
        ReflectionTestUtils.setField(factory, "localDefaultProvider", "sherpa-onnx");
    }

    @Test
    void roleWithoutConfigUsesLocalRecognition() {
        factory.getSttService(role(null));

        assertThat(creations).containsExactly("sherpa-onnx:-1");
        verify(configService, never()).getBO(any());
    }

    @Test
    void enabledConfigIsUsed() {
        when(configService.getBO(STT_ID)).thenReturn(new ConfigBO().setConfigId(STT_ID).setProvider("aliyun")
            .setState(ConfigBO.STATE_ENABLED));

        factory.getSttService(role(STT_ID));

        assertThat(creations).containsExactly("aliyun:40");
    }

    @Test
    void disabledConfigFallsBackToTheDefault() {
        when(configService.getBO(STT_ID)).thenReturn(new ConfigBO().setConfigId(STT_ID).setProvider("aliyun")
            .setState(ConfigBO.STATE_DISABLED));

        assertThat(factory.getSttService(role(STT_ID))).isNotNull();
        assertThat(creations).containsExactly("sherpa-onnx:-1");
    }

    @Test
    void deletedConfigFallsBackToTheDefault() {
        when(configService.getBO(STT_ID)).thenReturn(null);

        assertThat(factory.getSttService(role(STT_ID))).isNotNull();
        assertThat(creations).containsExactly("sherpa-onnx:-1");
    }

    /** 默认识别也建不起来：返回 null 让这一轮按「听不清」降级，不能把整条连接拖垮 */
    @Test
    void anUnavailableDefaultYieldsNull() {
        localModelMissing = true;
        when(configService.getBO(STT_ID)).thenReturn(null);

        assertThat(factory.getSttService(role(STT_ID))).isNull();
        assertThat(factory.getSttService(role(null))).isNull();
    }

    private static RoleBO role(Integer sttId) {
        RoleBO role = new RoleBO();
        role.setRoleName("小智");
        role.setSttId(sttId);
        return role;
    }
}
