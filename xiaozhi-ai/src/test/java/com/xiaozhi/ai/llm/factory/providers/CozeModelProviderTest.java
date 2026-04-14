package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.port.ConfigLookup;
import com.xiaozhi.common.port.TokenResolver;
import com.xiaozhi.ai.llm.providers.CozeChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CozeModelProviderTest {

    @Mock
    private ConfigLookup configLookup;

    @Mock
    private TokenResolver tokenResolver;

    private CozeModelProvider cozeModelProvider;

    @BeforeEach
    void setUp() {
        cozeModelProvider = new CozeModelProvider();
        ReflectionTestUtils.setField(cozeModelProvider, "configLookup", configLookup);
        ReflectionTestUtils.setField(cozeModelProvider, "tokenResolver", tokenResolver);
    }

    @Test
    void createChatModelLoadsAgentConfigThroughPort() {
        ConfigBO modelConfig = new ConfigBO()
                .setUserId(7)
                .setConfigName("bot-1")
                .setProvider("coze");
        ConfigBO agentConfig = new ConfigBO().setConfigId(5).setProvider("coze");
        when(configLookup.listConfigs(7, "agent", "coze", null, null, ConfigBO.STATE_ENABLED))
                .thenReturn(List.of(agentConfig));
        when(tokenResolver.getToken(agentConfig)).thenReturn("token-1");

        ChatModel chatModel = cozeModelProvider.createChatModel(modelConfig, new RoleBO());

        assertInstanceOf(CozeChatModel.class, chatModel);
        verify(tokenResolver).getToken(agentConfig);
    }

    @Test
    void createChatModelThrowsWhenAgentConfigMissing() {
        ConfigBO modelConfig = new ConfigBO()
                .setUserId(9)
                .setConfigName("bot-2")
                .setProvider("coze");
        when(configLookup.listConfigs(9, "agent", "coze", null, null, ConfigBO.STATE_ENABLED))
                .thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> cozeModelProvider.createChatModel(modelConfig, new RoleBO()));
    }
}
