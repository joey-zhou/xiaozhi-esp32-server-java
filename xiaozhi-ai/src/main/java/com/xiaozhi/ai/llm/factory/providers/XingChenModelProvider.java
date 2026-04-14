package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.ai.llm.factory.ChatModelProvider;
import com.xiaozhi.ai.llm.providers.XingChenChatModel;
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
 * 星辰(讯飞)模型提供者
 */
@Component
public class XingChenModelProvider implements ChatModelProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(XingChenModelProvider.class);
    
    @Autowired
    private ConfigLookup configLookup;
    
    @Override
    public String getProviderName() {
        return "xingchen";
    }
    
    @Override
    public ChatModel createChatModel(ConfigBO config, RoleBO role) {
        String endpoint = config.getApiUrl();
        
        // XingChen需要查询agent配置获取ApiKey和Secret
        List<ConfigBO> configs = configLookup.listConfigs(
                config.getUserId(),
                "agent",
                "xingchen",
                null,
                null,
                ConfigBO.STATE_ENABLED);
        if (configs == null || configs.isEmpty()) {
            throw new IllegalStateException("未找到XingChen agent配置, userId=" + config.getUserId());
        }
        ConfigBO queryConfig = configs.get(0);
        String apiKey = queryConfig.getApiKey();
        String apiSecret = queryConfig.getApiSecret();
        
        var chatModel = new XingChenChatModel(endpoint, apiKey, apiSecret);
        
        logger.info("Created XingChen ChatModel: endpoint={}", endpoint);
        return chatModel;
    }
}
