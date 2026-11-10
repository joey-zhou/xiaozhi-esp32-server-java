package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.ai.llm.factory.ChatModelProvider;
import com.xiaozhi.ai.llm.providers.CozeChatModel;
import com.xiaozhi.common.port.ProviderTokenClient;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.config.service.ConfigService;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
/**
 * Coze模型提供者
 */
@Slf4j
@Component
public class CozeModelProvider implements ChatModelProvider {
    
    @Autowired
    private ConfigService configService;
    
    @Autowired
    private ProviderTokenClient tokenClient;

    @Override
    public String getProviderName() {
        return "coze";
    }
    
    @Override
    public ChatModel createChatModel(ConfigBO config, RoleBO role) {
        String model = config.getConfigName();
        
        // coze 的智能体配置不带凭据，token 取自同步出它的那份平台凭据
        ConfigBO credential = configService.getAgentCredential(config);
        if (credential == null) {
            throw new IllegalStateException("未找到Coze平台凭据, configId=" + config.getConfigId());
        }
        var chatModel = new CozeChatModel(() -> tokenClient.getToken(credential), model);
        
        log.info("Created Coze ChatModel: model={}", model);
        return chatModel;
    }
}
