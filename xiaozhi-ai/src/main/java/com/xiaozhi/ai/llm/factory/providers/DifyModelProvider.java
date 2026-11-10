package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.ai.llm.factory.ChatModelProvider;
import com.xiaozhi.ai.llm.providers.DifyChatModel;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.config.service.ConfigService;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
/**
 * Dify模型提供者
 */
@Slf4j
@Component
public class DifyModelProvider implements ChatModelProvider {
    
    @Autowired
    private ConfigService configService;

    @Override
    public String getProviderName() {
        return "dify";
    }
    
    @Override
    public ChatModel createChatModel(ConfigBO config, RoleBO role) {
        ConfigBO credential = configService.getAgentCredential(config);
        if (credential == null) {
            throw new IllegalStateException("未找到Dify平台凭据, configId=" + config.getConfigId());
        }
        String endpoint = credential.getApiUrl();

        var chatModel = new DifyChatModel(endpoint, credential.getApiKey());
        
        log.info("Created Dify ChatModel: endpoint={}", endpoint);
        return chatModel;
    }
}
