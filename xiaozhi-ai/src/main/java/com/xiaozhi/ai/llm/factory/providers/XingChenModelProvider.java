package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.ai.llm.factory.ChatModelProvider;
import com.xiaozhi.ai.llm.providers.XingChenChatModel;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.port.ConfigLookup;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
/**
 * 星辰(讯飞)模型提供者
 */
@Slf4j
@Component
public class XingChenModelProvider implements ChatModelProvider {

    @Autowired
    private ConfigLookup configLookup;

    @Lazy
    @Autowired
    private ToolCallingManager toolCallingManager;

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
        // 控制台字段：apiKey 是授权码(APIKey:APISecret)，直接当 Bearer token 用；apiSecret 实际存的是 FlowId
        String bearerToken = queryConfig.getApiKey();
        String flowId = queryConfig.getApiSecret();

        var chatModel = new XingChenChatModel(endpoint, bearerToken, flowId, toolCallingManager);
        
        log.info("Created XingChen ChatModel: endpoint={}", endpoint);
        return chatModel;
    }
}
