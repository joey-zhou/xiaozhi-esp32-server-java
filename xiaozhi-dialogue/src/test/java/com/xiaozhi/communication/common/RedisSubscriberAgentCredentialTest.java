package com.xiaozhi.communication.common;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.port.ProviderTokenClient;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.dialogue.llm.factory.PersonaFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台凭据（configType agent）改了、停用或删了，挂在它下面的智能体在本实例上的模型与在线会话都要清：
 * 模型实例里还是改前的凭据，会话里的 Persona 拿着那个实例
 */
@ExtendWith(MockitoExtension.class)
class RedisSubscriberAgentCredentialTest {

    @Mock
    private ConfigService configService;

    @Mock
    private PersonaFactory personaFactory;

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private ProviderTokenClient tokenClient;

    private RedisSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new RedisSubscriber();
        ReflectionTestUtils.setField(subscriber, "configService", configService);
        ReflectionTestUtils.setField(subscriber, "personaFactory", personaFactory);
        ReflectionTestUtils.setField(subscriber, "chatModelFactory", chatModelFactory);
        ReflectionTestUtils.setField(subscriber, "tokenClient", tokenClient);
    }

    @Test
    void credentialChangeClearsTheAgentsHangingOnIt() {
        ConfigBO credential = new ConfigBO().setConfigId(5).setUserId(7).setConfigType("agent").setProvider("coze");
        when(configService.getBO(5)).thenReturn(credential);
        when(configService.listAgentModels(credential)).thenReturn(List.of(
            new ConfigBO().setConfigId(20), new ConfigBO().setConfigId(21)));

        subscriber.onConfigChanged("{\"configType\":\"agent\",\"configId\":5}");

        verify(personaFactory).clearPersonasByModelId(20);
        verify(personaFactory).clearPersonasByModelId(21);
        verify(chatModelFactory).removeCache(20);
        verify(chatModelFactory).removeCache(21);
        verify(tokenClient).removeCache(credential);
    }
}
