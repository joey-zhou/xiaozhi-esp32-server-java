package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.ai.llm.factory.ChatModelProvider;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Ollama模型提供者
 */
@Component
public class OllamaModelProvider implements ChatModelProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(OllamaModelProvider.class);
    
    @Autowired
    private ToolCallingManager toolCallingManager;
    
    @Override
    public String getProviderName() {
        return "ollama";
    }
    
    @Override
    public ChatModel createChatModel(ConfigBO config, RoleBO role) {
        String endpoint = config.getApiUrl();
        String model = config.getConfigName();
        Double temperature = role.getTemperature();
        Double topP = role.getTopP();
        
        var ollamaApi = OllamaApi.builder()
                .baseUrl(endpoint)
                .build();
        
        var ollamaOptions = OllamaChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .topP(topP)
                .build();
        
        var chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(ollamaOptions)
                .toolCallingManager(toolCallingManager)
                .build();
        
        logger.info("Created Ollama ChatModel: model={}, endpoint={}", model, endpoint);
        return chatModel;
    }

    @Override
    public EmbeddingModel createEmbeddingModel(ConfigBO config) {
        var ollamaApi = OllamaApi.builder().baseUrl(config.getApiUrl()).build();
        var options = OllamaEmbeddingOptions.builder().model(config.getConfigName()).build();
        logger.info("Created Ollama EmbeddingModel: model={}, endpoint={}", config.getConfigName(), config.getApiUrl());
        return OllamaEmbeddingModel.builder().ollamaApi(ollamaApi).defaultOptions(options).build();
    }
}

