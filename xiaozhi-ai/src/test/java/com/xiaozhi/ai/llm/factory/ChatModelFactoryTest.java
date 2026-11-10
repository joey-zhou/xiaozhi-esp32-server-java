package com.xiaozhi.ai.llm.factory;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.event.AiConfigChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * 钉住工厂的两件事：按 provider 名路由（找不到时回落 openai），
 * 以及 chatModel / embeddingModel 两级缓存的命中与配置变更后的失效，
 * 缓存失效漏掉会导致改了模型配置不生效。
 */
@ExtendWith(MockitoExtension.class)
class ChatModelFactoryTest {

    @Mock
    private ConfigService configService;

    @Mock
    private ChatModelProvider stubProvider;

    @Mock
    private ChatModelProvider openAiProvider;

    @Mock
    private ChatModel stubModel;

    @Mock
    private ChatModel fallbackModel;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private EmbeddingModel rebuiltEmbeddingModel;

    private ChatModelFactory chatModelFactory;

    @BeforeEach
    void setUp() {
        when(stubProvider.getProviderName()).thenReturn("stub");
        when(openAiProvider.getProviderName()).thenReturn("openai");

        chatModelFactory = new ChatModelFactory(List.of(stubProvider, openAiProvider));
        ReflectionTestUtils.setField(chatModelFactory, "configService", configService);
    }

    @Test
    void getChatModelUsesTheRolesConfigAndMatchedProvider() {
        RoleBO role = new RoleBO();
        role.setModelId(11);
        ConfigBO config = new ConfigBO().setConfigId(11).setProvider("stub");
        when(configService.getConfig(11)).thenReturn(config);
        when(stubProvider.createChatModel(any(), any())).thenReturn(stubModel);

        ChatModel result = chatModelFactory.getChatModel(role);

        assertThat(result).isSameAs(stubModel);
        verify(configService).getConfig(11);
        verify(stubProvider).createChatModel(config, role);
    }

    @Test
    void getChatModelFallsBackToOpenAiProviderWhenSpecificProviderMissing() {
        RoleBO role = new RoleBO();
        role.setModelId(22);
        ConfigBO config = new ConfigBO().setConfigId(22).setProvider("unknown");
        when(configService.getConfig(22)).thenReturn(config);
        when(openAiProvider.createChatModel(any(), any())).thenReturn(fallbackModel);

        ChatModel result = chatModelFactory.getChatModel(role);

        assertThat(result).isSameAs(fallbackModel);
        verify(openAiProvider).createChatModel(config, role);
    }

    /**
     * 角色绑定的对话模型被停用后不能再拿它外呼计费；回退默认对话模型，与 TTS 停用时回退默认的处理一致
     */
    @Test
    void disabledChatModelFallsBackToTheDefaultOne() {
        RoleBO role = new RoleBO();
        role.setModelId(11);
        when(configService.getConfig(11)).thenReturn(new ConfigBO().setConfigId(11).setProvider("stub")
            .setState(ConfigBO.STATE_DISABLED));
        ConfigBO defaultConfig = new ConfigBO().setConfigId(99).setProvider("stub");
        when(configService.getDefaultConfig("llm", ConfigBO.ModelType.chat.getValue())).thenReturn(defaultConfig);
        when(stubProvider.createChatModel(any(), any())).thenReturn(stubModel);

        chatModelFactory.getChatModel(role);

        verify(stubProvider).createChatModel(defaultConfig, role);
    }

    /** 回退出来的模型挂在默认配置的名下：默认配置变更时它要跟着失效，原配置重新启用时不能继续用它 */
    @Test
    void fallbackModelIsCachedUnderTheDefaultConfig() {
        RoleBO role = new RoleBO();
        role.setModelId(11);
        when(configService.getConfig(11)).thenReturn(new ConfigBO().setConfigId(11).setState(ConfigBO.STATE_DISABLED));
        when(configService.getDefaultConfig("llm", ConfigBO.ModelType.chat.getValue()))
            .thenReturn(new ConfigBO().setConfigId(99).setProvider("stub"));
        when(stubProvider.createChatModel(any(), any())).thenReturn(stubModel, fallbackModel);

        chatModelFactory.getChatModel(role);
        chatModelFactory.removeCache(99);
        ChatModel rebuilt = chatModelFactory.getChatModel(role);

        assertThat(rebuilt).isSameAs(fallbackModel);
    }

    @Test
    void disabledChatModelWithoutADefaultFailsWithAReadableMessage() {
        RoleBO role = new RoleBO();
        role.setRoleName("小助手");
        role.setModelId(11);
        when(configService.getConfig(11)).thenReturn(new ConfigBO().setConfigId(11).setState(ConfigBO.STATE_DISABLED));
        when(configService.getDefaultConfig("llm", ConfigBO.ModelType.chat.getValue())).thenReturn(null);

        assertThatThrownBy(() -> chatModelFactory.getChatModel(role))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("小助手")
            .hasMessageContaining("已停用");
        verify(stubProvider, never()).createChatModel(any(), any());
    }

    @Test
    void getEmbeddingModelIsCachedByConfigId() {
        ConfigBO config = new ConfigBO().setConfigId(33).setProvider("stub");
        when(configService.getConfig(33)).thenReturn(config);
        when(stubProvider.createEmbeddingModel(config)).thenReturn(embeddingModel);

        EmbeddingModel first = chatModelFactory.getEmbeddingModel(33);
        EmbeddingModel second = chatModelFactory.getEmbeddingModel(33);

        assertThat(first).isSameAs(embeddingModel);
        assertThat(second).isSameAs(first);
        verify(configService, times(1)).getConfig(33);
        verify(stubProvider, times(1)).createEmbeddingModel(config);
    }

    @Test
    void removeCacheRebuildsEmbeddingModel() {
        ConfigBO config = new ConfigBO().setConfigId(44).setProvider("stub");
        when(configService.getConfig(44)).thenReturn(config);
        when(stubProvider.createEmbeddingModel(config)).thenReturn(embeddingModel, rebuiltEmbeddingModel);

        EmbeddingModel before = chatModelFactory.getEmbeddingModel(44);
        chatModelFactory.removeCache(44);
        EmbeddingModel after = chatModelFactory.getEmbeddingModel(44);

        assertThat(before).isSameAs(embeddingModel);
        assertThat(after).isSameAs(rebuiltEmbeddingModel);
        assertThat(after).isNotSameAs(before);
    }

    @Test
    void getChatModelIsCachedByModelIdAndRoleParams() {
        RoleBO role = new RoleBO();
        role.setModelId(55);
        ConfigBO config = new ConfigBO().setConfigId(55).setProvider("stub");
        when(configService.getConfig(55)).thenReturn(config);
        when(stubProvider.createChatModel(config, role)).thenReturn(stubModel);

        ChatModel first = chatModelFactory.getChatModel(role);
        ChatModel second = chatModelFactory.getChatModel(role);

        assertThat(second).isSameAs(first);
        verify(stubProvider, times(1)).createChatModel(config, role);
    }

    // removeCache 按 configId + ":" 前缀清理 chatModelCache，modelId=11 的条目不能被 removeCache(1) 连带删掉
    @Test
    void removeCacheDropsOnlyChatModelsOfThatModelId() {
        RoleBO role = new RoleBO();
        role.setModelId(1);
        RoleBO otherRole = new RoleBO();
        otherRole.setModelId(11);
        ConfigBO config = new ConfigBO().setConfigId(1).setProvider("stub");
        ConfigBO otherConfig = new ConfigBO().setConfigId(11).setProvider("stub");
        when(configService.getConfig(1)).thenReturn(config);
        when(configService.getConfig(11)).thenReturn(otherConfig);
        when(stubProvider.createChatModel(config, role)).thenReturn(stubModel, fallbackModel);
        when(stubProvider.createChatModel(otherConfig, otherRole)).thenReturn(stubModel);

        chatModelFactory.getChatModel(role);
        ChatModel cachedOther = chatModelFactory.getChatModel(otherRole);
        chatModelFactory.removeCache(1);

        assertThat(chatModelFactory.getChatModel(role)).isSameAs(fallbackModel);
        assertThat(chatModelFactory.getChatModel(otherRole)).isSameAs(cachedOther);
        verify(stubProvider, times(2)).createChatModel(config, role);
        verify(stubProvider, times(1)).createChatModel(otherConfig, otherRole);
    }

    @Test
    void credentialChangeRebuildsTheAgentsHangingOnIt() {
        RoleBO role = new RoleBO();
        role.setModelId(60);
        ConfigBO agent = new ConfigBO().setConfigId(60).setProvider("stub");
        ConfigBO credential = new ConfigBO().setConfigId(5).setConfigType("agent").setProvider("stub");
        when(configService.getConfig(60)).thenReturn(agent);
        when(configService.getBO(5)).thenReturn(credential);
        when(configService.listAgentModels(credential)).thenReturn(List.of(agent));
        when(stubProvider.createChatModel(agent, role)).thenReturn(stubModel, fallbackModel);

        chatModelFactory.getChatModel(role);
        chatModelFactory.onAiConfigChanged(new AiConfigChangedEvent(this, "agent", 5));

        // 缓存里那个实例还拿着改前的凭据
        assertThat(chatModelFactory.getChatModel(role)).isSameAs(fallbackModel);
    }

    @Test
    void getVisionModelUsesDefaultLookup() {
        ConfigBO config = new ConfigBO().setConfigId(33).setProvider("stub");
        when(configService.getDefaultConfig("llm", ConfigBO.ModelType.vision.getValue())).thenReturn(config);
        when(stubProvider.createChatModel(any(), any())).thenReturn(stubModel);

        ChatModel result = chatModelFactory.getVisionModel();

        assertThat(result).isSameAs(stubModel);
        verify(configService).getDefaultConfig("llm", ConfigBO.ModelType.vision.getValue());
    }
}
