package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.ai.llm.factory.ChatModelProvider;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 智谱AI模型提供者
 */
@Component
public class ZhiPuModelProvider implements ChatModelProvider {

    private static final Logger logger = LoggerFactory.getLogger(ZhiPuModelProvider.class);

    @Lazy
    @Autowired
    private ToolCallingManager toolCallingManager;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Override
    public String getProviderName() {
        return "zhipu";
    }

    @Override
    public ChatModel createChatModel(ConfigBO config, RoleBO role) {
        String endpoint = config.getApiUrl();
        String apiKey = config.getApiKey();
        String model = config.getConfigName();
        Double temperature = role.getTemperature();
        Double topP = role.getTopP();

        var zhiPuAiApi = ZhiPuAiApi.builder().baseUrl(endpoint).apiKey(apiKey).build();

        var zhipuAiChatOptions = ZhiPuAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .topP(topP)
                .build();

        var chatModel = new ZhiPuAiChatModel(zhiPuAiApi, zhipuAiChatOptions, toolCallingManager, RetryUtils.DEFAULT_RETRY_TEMPLATE, observationRegistry);

        logger.info("Created ZhiPu ChatModel: model={}, endpoint={}", model, endpoint);
        return chatModel;
    }

    @Override
    public EmbeddingModel createEmbeddingModel(ConfigBO config) {
        var zhiPuAiApi = ZhiPuAiApi.builder().baseUrl(config.getApiUrl()).apiKey(config.getApiKey()).build();
        var options = ZhiPuAiEmbeddingOptions.builder().model(config.getConfigName()).build();
        logger.info("Created ZhiPu EmbeddingModel: model={}, endpoint={}", config.getConfigName(), config.getApiUrl());
        return new ZhiPuAiEmbeddingModel(zhiPuAiApi, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }
}

