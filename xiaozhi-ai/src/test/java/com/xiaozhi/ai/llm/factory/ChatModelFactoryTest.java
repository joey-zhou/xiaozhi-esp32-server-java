package com.xiaozhi.ai.llm.factory;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.port.ConfigLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class ChatModelFactoryTest {

    @Mock
    private ConfigLookup configLookup;

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
        ReflectionTestUtils.setField(chatModelFactory, "configLookup", configLookup);
    }

    @Test
    void getChatModelUsesConfigLookupAndMatchedProvider() {
        RoleBO role = new RoleBO();
        role.setModelId(11);
        ConfigBO config = new ConfigBO().setConfigId(11).setProvider("stub");
        when(configLookup.getConfig(11)).thenReturn(config);
        when(stubProvider.createChatModel(any(), any())).thenReturn(stubModel);

        ChatModel result = chatModelFactory.getChatModel(role);

        assertSame(stubModel, result);
        verify(configLookup).getConfig(11);
        verify(stubProvider).createChatModel(config, role);
    }

    @Test
    void getChatModelFallsBackToOpenAiProviderWhenSpecificProviderMissing() {
        RoleBO role = new RoleBO();
        role.setModelId(22);
        ConfigBO config = new ConfigBO().setConfigId(22).setProvider("unknown");
        when(configLookup.getConfig(22)).thenReturn(config);
        when(openAiProvider.createChatModel(any(), any())).thenReturn(fallbackModel);

        ChatModel result = chatModelFactory.getChatModel(role);

        assertSame(fallbackModel, result);
        verify(openAiProvider).createChatModel(config, role);
    }

    @Test
    void getEmbeddingModelIsCachedByConfigId() {
        ConfigBO config = new ConfigBO().setConfigId(33).setProvider("stub");
        when(configLookup.getConfig(33)).thenReturn(config);
        when(stubProvider.createEmbeddingModel(config)).thenReturn(embeddingModel);

        EmbeddingModel first = chatModelFactory.getEmbeddingModel(33);
        EmbeddingModel second = chatModelFactory.getEmbeddingModel(33);

        assertSame(embeddingModel, first);
        assertSame(first, second);
        verify(configLookup, times(1)).getConfig(33);
        verify(stubProvider, times(1)).createEmbeddingModel(config);
    }

    @Test
    void removeCacheRebuildsEmbeddingModel() {
        ConfigBO config = new ConfigBO().setConfigId(44).setProvider("stub");
        when(configLookup.getConfig(44)).thenReturn(config);
        when(stubProvider.createEmbeddingModel(config)).thenReturn(embeddingModel, rebuiltEmbeddingModel);

        EmbeddingModel before = chatModelFactory.getEmbeddingModel(44);
        chatModelFactory.removeCache(44);
        EmbeddingModel after = chatModelFactory.getEmbeddingModel(44);

        assertSame(embeddingModel, before);
        assertSame(rebuiltEmbeddingModel, after);
        assertNotSame(before, after);
    }

    @Test
    void getVisionModelUsesDefaultLookup() {
        ConfigBO config = new ConfigBO().setConfigId(33).setProvider("stub");
        when(configLookup.getDefaultConfig("llm", ConfigBO.ModelType.vision.getValue())).thenReturn(config);
        when(stubProvider.createChatModel(any(), any())).thenReturn(stubModel);

        ChatModel result = chatModelFactory.getVisionModel();

        assertSame(stubModel, result);
        verify(configLookup).getDefaultConfig("llm", ConfigBO.ModelType.vision.getValue());
    }
}
