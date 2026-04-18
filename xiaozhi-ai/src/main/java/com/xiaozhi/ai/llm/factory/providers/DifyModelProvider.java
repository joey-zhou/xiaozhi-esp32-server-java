package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.ai.llm.factory.ChatModelProvider;
import com.xiaozhi.ai.llm.providers.DifyChatModel;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.port.ConfigLookup;

import java.util.List;
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
    private ConfigLookup configLookup;
    
    @Override
    public String getProviderName() {
        return "dify";
    }
    
    @Override
    public ChatModel createChatModel(ConfigBO config, RoleBO role) {
        String endpoint = config.getApiUrl();
        
        // Dify需要查询agent配置获取ApiKey
        List<ConfigBO> configs = configLookup.listConfigs(
                config.getUserId(),
                "agent",
                "dify",
                null,
                null,
                ConfigBO.STATE_ENABLED);
        if (configs == null || configs.isEmpty()) {
            throw new IllegalStateException("未找到Dify agent配置, userId=" + config.getUserId());
        }
        ConfigBO queryConfig = configs.get(0);
        String apiKey = queryConfig.getApiKey();
        
        var chatModel = new DifyChatModel(endpoint, apiKey);
        
        log.info("Created Dify ChatModel: endpoint={}", endpoint);
        return chatModel;
    }
}
