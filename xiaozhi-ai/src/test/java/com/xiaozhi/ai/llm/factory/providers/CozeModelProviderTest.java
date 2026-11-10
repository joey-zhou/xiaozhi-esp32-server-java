package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.port.ProviderTokenClient;
import com.xiaozhi.ai.llm.providers.CozeChatModel;
import com.xiaozhi.config.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CozeModelProviderTest {

    @Mock
    private ConfigService configService;

    @Mock
    private ProviderTokenClient tokenClient;

    private CozeModelProvider cozeModelProvider;

    @BeforeEach
    void setUp() {
        cozeModelProvider = new CozeModelProvider();
        ReflectionTestUtils.setField(cozeModelProvider, "configService", configService);
        ReflectionTestUtils.setField(cozeModelProvider, "tokenClient", tokenClient);
    }


    @Test
    void createChatModelUsesTheAgentsPlatformCredential() {
        ConfigBO modelConfig = new ConfigBO()
                .setUserId(7)
                .setConfigName("bot-1")
                .setProvider("coze");
        ConfigBO agentConfig = new ConfigBO().setConfigId(5).setProvider("coze");
        when(configService.getAgentCredential(modelConfig)).thenReturn(agentConfig);

        ChatModel chatModel = cozeModelProvider.createChatModel(modelConfig, new RoleBO());

        assertThat(chatModel).isInstanceOf(CozeChatModel.class);
        // 建模型时不取 token，发请求时才取
        verifyNoInteractions(tokenClient);
    }

    @Test
    void tokenIsFetchedForEveryRequestInsteadOfBakedIntoTheModel() {
        ConfigBO modelConfig = new ConfigBO().setUserId(7).setConfigName("bot-1").setProvider("coze");
        ConfigBO agentConfig = new ConfigBO().setConfigId(5).setProvider("coze");
        when(configService.getAgentCredential(modelConfig)).thenReturn(agentConfig);
        // 模型实例缓存得比 token 有效期长，续期后的新 token 要能用上
        when(tokenClient.getToken(agentConfig)).thenReturn("token-1", "token-2");

        List<Object> tokenSources = new ArrayList<>();
        try (MockedConstruction<CozeChatModel> ignored = mockConstruction(CozeChatModel.class,
                (model, context) -> tokenSources.add(context.arguments().getFirst()))) {
            cozeModelProvider.createChatModel(modelConfig, new RoleBO());
        }

        assertThat(tokenSources).singleElement().isInstanceOf(Supplier.class);
        Supplier<?> accessToken = (Supplier<?>) tokenSources.getFirst();
        assertThat(accessToken.get()).isEqualTo("token-1");
        assertThat(accessToken.get()).isEqualTo("token-2");
    }

    @Test
    void createChatModelThrowsWhenAgentConfigMissing() {
        ConfigBO modelConfig = new ConfigBO()
                .setUserId(9)
                .setConfigName("bot-2")
                .setProvider("coze");

        assertThatThrownBy(() -> cozeModelProvider.createChatModel(modelConfig, new RoleBO()))
                .isInstanceOf(IllegalStateException.class);
    }
}
