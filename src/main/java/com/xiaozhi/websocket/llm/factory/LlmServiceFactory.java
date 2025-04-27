package com.xiaozhi.websocket.llm.factory;

import com.xiaozhi.websocket.llm.api.LlmService;
import com.xiaozhi.websocket.llm.providers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM服务工厂
 * 根据提供商创建对应的LLM服务
 */
public class LlmServiceFactory {
    private static final Logger logger = LoggerFactory.getLogger(LlmServiceFactory.class);

    /**
     * 创建LLM服务
     *
     * @param provider 提供商
     * @param endpoint API端点
     * @param apiKey   API密钥
     * @param model    模型名称
     * @return LLM服务
     */
    public static LlmService createLlmService(String provider, String endpoint, String appId, String apiKey,
                                              String apiSecret, String model) {
        provider = provider.toLowerCase();
        switch (provider) {
            case "openai":
                return new OpenAiService(endpoint, appId, apiKey, apiSecret, model);
            case "ollama":
                return new OllamaService(endpoint, appId, apiKey, apiSecret, model);
            case "spark":
                return new SparkService(endpoint, appId, apiKey, apiSecret, model);
            case "coze":
                return new CozeService(endpoint, appId, apiKey, apiSecret, model);
            case "zhipu":
                return new ZhiPuService(endpoint, appId, apiKey, apiSecret, model);
            // 可以添加更多提供商的支持
            default:
                // logger.info("未找到匹配的模型提供商 '{}', 默认使用Ollama", provider);
                // return new OllamaService(endpoint, appId, apiKey, apiSecret, model);
                throw new IllegalArgumentException("未找到匹配的模型提供商 '" + provider + "'");
        }
    }
}