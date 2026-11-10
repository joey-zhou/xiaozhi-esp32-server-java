package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.ai.llm.providers.CozeChatModel;
import com.xiaozhi.ai.llm.providers.DifyChatModel;
import com.xiaozhi.ai.llm.providers.XingChenChatModel;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.port.ProviderTokenClient;
import com.xiaozhi.config.service.impl.ConfigServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 同一用户配了多份平台凭据时，每个智能体都要走同步出它的那份：
 * dify、xingchen 一份凭据一个智能体，不能都落到查询结果里的第一份；coze 与同步取同一份
 */
class AgentPlatformCredentialRoutingTest {

    private static final int USER_ID = 7;

    /** 凭据怎么选走真实实现，只替换掉查库 */
    private final ConfigServiceImpl configService = spy(new ConfigServiceImpl());

    @Test
    void difyAgentUsesTheCredentialItWasSyncedFrom() {
        credentials("dify", credential(1, "key-a", "https://a.example.com/v1", null),
                credential(2, "key-b", "https://b.example.com/v1", null));
        DifyModelProvider provider = new DifyModelProvider();
        ReflectionTestUtils.setField(provider, "configService", configService);

        List<List<?>> arguments = new ArrayList<>();
        try (MockedConstruction<DifyChatModel> ignored = mockConstruction(DifyChatModel.class,
                (model, context) -> arguments.add(context.arguments()))) {
            provider.createChatModel(agent("dify", "key-b"), new RoleBO());
        }

        assertThat(arguments).containsExactly(List.of("https://b.example.com/v1", "key-b"));
    }

    @Test
    void xingChenAgentUsesTheCredentialItWasSyncedFrom() {
        credentials("xingchen", credential(1, "key-a", "https://a.example.com", "flow-a"),
                credential(2, "key-b", "https://b.example.com", "flow-b"));
        XingChenModelProvider provider = new XingChenModelProvider();
        ReflectionTestUtils.setField(provider, "configService", configService);

        List<List<?>> arguments = new ArrayList<>();
        try (MockedConstruction<XingChenChatModel> ignored = mockConstruction(XingChenChatModel.class,
                (model, context) -> arguments.add(context.arguments().subList(0, 3)))) {
            provider.createChatModel(agent("xingchen", "key-b"), new RoleBO());
        }

        assertThat(arguments).containsExactly(List.of("https://b.example.com", "key-b", "flow-b"));
    }

    @Test
    void agentWhoseCredentialIsGoneDoesNotBorrowAnother() {
        credentials("dify", credential(1, "key-a", "https://a.example.com/v1", null));
        DifyModelProvider provider = new DifyModelProvider();
        ReflectionTestUtils.setField(provider, "configService", configService);

        try (MockedConstruction<DifyChatModel> built = mockConstruction(DifyChatModel.class)) {
            assertThatThrownBy(() -> provider.createChatModel(agent("dify", "key-removed"), new RoleBO()))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(built.constructed()).isEmpty();
        }
    }

    @Test
    void cozeAgentUsesTheCredentialTheSyncUses() {
        ConfigBO newer = credential(9, null, null, "space-2");
        ConfigBO older = credential(4, null, null, "space-1");
        credentials("coze", newer, older);
        ProviderTokenClient tokenClient = mock(ProviderTokenClient.class);
        when(tokenClient.getToken(older)).thenReturn("token-1");
        CozeModelProvider provider = new CozeModelProvider();
        ReflectionTestUtils.setField(provider, "configService", configService);
        ReflectionTestUtils.setField(provider, "tokenClient", tokenClient);

        List<Supplier<?>> tokenSources = new ArrayList<>();
        try (MockedConstruction<CozeChatModel> ignored = mockConstruction(CozeChatModel.class,
                (model, context) -> tokenSources.add((Supplier<?>) context.arguments().getFirst()))) {
            provider.createChatModel(agent("coze", null).setConfigName("bot-1"), new RoleBO());
        }

        assertThat(tokenSources).singleElement().extracting(Supplier::get).isEqualTo("token-1");
        verify(tokenClient).getToken(older);
        assertThat(configService.getPlatformCredential(USER_ID, "coze")).isSameAs(older);
    }

    private void credentials(String provider, ConfigBO... credentials) {
        for (ConfigBO credential : credentials) {
            credential.setProvider(provider);
        }
        doReturn(List.of(credentials)).when(configService)
                .listBO(USER_ID, "agent", provider, null, null, ConfigBO.STATE_ENABLED);
    }

    private static ConfigBO credential(int configId, String apiKey, String apiUrl, String apiSecret) {
        return new ConfigBO().setConfigId(configId).setUserId(USER_ID).setConfigType("agent")
                .setApiKey(apiKey).setApiUrl(apiUrl).setApiSecret(apiSecret);
    }

    private static ConfigBO agent(String provider, String apiKey) {
        return new ConfigBO().setConfigId(100).setUserId(USER_ID).setConfigType("llm").setProvider(provider)
                .setApiKey(apiKey);
    }
}
