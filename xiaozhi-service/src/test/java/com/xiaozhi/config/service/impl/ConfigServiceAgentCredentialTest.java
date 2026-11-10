package com.xiaozhi.config.service.impl;

import com.xiaozhi.common.model.bo.ConfigBO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 智能体与平台凭据的对应：dify、xingchen 按 apiKey 认，coze 取用户最早建的那份；
 * 凭据改了 apiKey 或地址时它落成的智能体跟着改。查询必须限定用户，
 * 因为 listBO 在 userId 为空时会略过用户条件退化成全库查询，取到其他用户的凭据
 */
class ConfigServiceAgentCredentialTest {

    private static final int USER_ID = 7;

    private final ConfigServiceImpl service = spy(new ConfigServiceImpl());

    @Test
    void keyedPlatformAgentFindsTheCredentialWithItsApiKey() {
        ConfigBO other = credential(1, "dify", "key-a", "https://a.example.com/v1");
        ConfigBO own = credential(2, "dify", "key-b", "https://b.example.com/v1");
        stubList("agent", "dify", other, own);

        assertThat(service.getAgentCredential(agent("dify", "key-b"))).isSameAs(own);
        assertThat(service.getAgentCredential(agent("dify", "key-removed"))).isNull();
        assertThat(service.getAgentCredential(agent("dify", null))).isNull();
    }

    @Test
    void cozeAgentsShareTheOldestCredential() {
        ConfigBO newer = credential(9, "coze", null, null);
        ConfigBO older = credential(4, "coze", null, null);
        stubList("agent", "coze", newer, older);

        assertThat(service.getAgentCredential(agent("coze", null))).isSameAs(older);
        assertThat(service.getPlatformCredential(USER_ID, "coze")).isSameAs(older);
    }

    @Test
    void credentialLookupRequiresUser() {
        assertThatThrownBy(() -> service.getAgentCredential(agent("dify", "key-a").setUserId(null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getPlatformCredential(null, "coze"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listAgentModels(credential(1, "coze", null, null).setUserId(null)))
            .isInstanceOf(IllegalArgumentException.class);
        verify(service, never()).listBO(any(), any(), any(), any(), any(), any());
    }

    @Test
    void agentsHangingOnAKeyedCredentialAreTheOnesWithItsApiKey() {
        ConfigBO own = agent("dify", "key-a").setConfigId(20);
        ConfigBO other = agent("dify", "key-b").setConfigId(21);
        stubList("llm", "dify", own, other);

        assertThat(service.listAgentModels(credential(1, "dify", "key-a", null))).containsExactly(own);
        assertThat(service.listAgentModels(credential(1, "dify", null, null))).isEmpty();
    }

    @Test
    void agentsHangingOnACozeCredentialAreAllTheUsersCozeAgents() {
        ConfigBO first = agent("coze", null).setConfigId(20);
        ConfigBO second = agent("coze", null).setConfigId(21);
        stubList("llm", "coze", first, second);

        assertThat(service.listAgentModels(credential(4, "coze", null, null))).containsExactly(first, second);
    }

    @Test
    void editedApiKeyMovesItsAgentsAlong() {
        ConfigBO followed = agent("xingchen", "key-a").setConfigId(20).setApiUrl("https://old.example.com");
        ConfigBO unrelated = agent("xingchen", "key-other").setConfigId(21);
        stubList("llm", "xingchen", followed, unrelated);
        doReturn(null).when(service).saveAgentModel(any());

        service.followAgentCredential(credential(1, "xingchen", "key-a", "https://old.example.com"),
            credential(1, "xingchen", "key-new", "https://new.example.com"));

        ArgumentCaptor<ConfigBO> saved = ArgumentCaptor.forClass(ConfigBO.class);
        verify(service).saveAgentModel(saved.capture());
        assertThat(saved.getValue().getConfigId()).isEqualTo(20);
        assertThat(saved.getValue().getApiKey()).isEqualTo("key-new");
        assertThat(saved.getValue().getApiUrl()).isEqualTo("https://new.example.com");
    }

    @Test
    void credentialEditsThatAgentsDoNotCarryLeaveThemAlone() {
        // 只改了 FlowId：智能体配置上没有这个字段
        service.followAgentCredential(credential(1, "xingchen", "key-a", "https://a.example.com"),
            credential(1, "xingchen", "key-a", "https://a.example.com").setApiSecret("flow-new"));
        // coze 的智能体配置不带凭据字段
        service.followAgentCredential(credential(2, "coze", "key-a", null), credential(2, "coze", "key-b", null));
        // 不是平台凭据
        service.followAgentCredential(agent("dify", "key-a"), agent("dify", "key-b"));

        verify(service, never()).listBO(any(), eq("llm"), anyString(), isNull(), isNull(), anyString());
        verify(service, never()).saveAgentModel(any());
    }

    private void stubList(String configType, String provider, ConfigBO... configs) {
        doReturn(List.of(configs)).when(service)
            .listBO(USER_ID, configType, provider, null, null, ConfigBO.STATE_ENABLED);
    }

    private static ConfigBO credential(int configId, String provider, String apiKey, String apiUrl) {
        return new ConfigBO().setConfigId(configId).setUserId(USER_ID).setConfigType("agent")
            .setProvider(provider).setApiKey(apiKey).setApiUrl(apiUrl);
    }

    private static ConfigBO agent(String provider, String apiKey) {
        return new ConfigBO().setConfigId(100).setUserId(USER_ID).setConfigType("llm")
            .setProvider(provider).setApiKey(apiKey);
    }
}
