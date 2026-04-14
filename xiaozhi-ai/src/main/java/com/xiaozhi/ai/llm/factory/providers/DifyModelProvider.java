package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.ai.llm.factory.ChatModelProvider;
import com.xiaozhi.ai.llm.providers.DifyChatModel;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.port.ConfigLookup;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Dify模型提供者
 */
@Component
public class DifyModelProvider implements ChatModelProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(DifyModelProvider.class);
    
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
        
        logger.info("Created Dify ChatModel: endpoint={}", endpoint);
        return chatModel;
    }
}
