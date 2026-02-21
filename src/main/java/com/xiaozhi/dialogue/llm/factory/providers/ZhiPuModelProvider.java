package com.xiaozhi.dialogue.llm.factory.providers;

import com.xiaozhi.dialogue.llm.factory.ChatModelProvider;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysRole;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 智谱AI模型提供者
 */
@Component
public class ZhiPuModelProvider implements ChatModelProvider {

    private static final Logger logger = LoggerFactory.getLogger(ZhiPuModelProvider.class);

    @Autowired
    private ToolCallingManager toolCallingManager;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Override
    public String getProviderName() {
        return "zhipu";
    }

    @Override
    public ChatModel createChatModel(SysConfig config, SysRole role) {
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
}

