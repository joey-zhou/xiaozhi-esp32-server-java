package com.xiaozhi.ai.llm.factory.providers;

import com.xiaozhi.ai.llm.factory.ChatModelProvider;
import com.xiaozhi.ai.llm.providers.XingHuoChatModel;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 讯飞星火大模型提供者
 * 由于讯飞星火大模型不兼容OpenAI的Function Calling，因此特意创建该Provider以适配Function Calling功能
 * 支持模型: Lite(general), Pro(generalv3), Pro-128K(generalv3-128k), 
 *          Max(generalv3.5), Max-32K(generalv3.5-32k), 4.0Ultra(generalv4)
 */
@Component
public class XingHuoModelProvider implements ChatModelProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(XingHuoModelProvider.class);
    
    @Override
    public String getProviderName() {
        return "xinghuo";
    }
    
    @Override
    public ChatModel createChatModel(ConfigBO config, RoleBO role) {
        String apiPassword = config.getApiKey(); // 星火使用APIPassword作为认证
        String model = config.getConfigName(); // 如: general, generalv3, generalv3.5, generalv4
        
        var chatModel = new XingHuoChatModel(apiPassword, model);
        
        logger.info("Created XingHuo ChatModel: model={}", model);
        return chatModel;
    }
}

