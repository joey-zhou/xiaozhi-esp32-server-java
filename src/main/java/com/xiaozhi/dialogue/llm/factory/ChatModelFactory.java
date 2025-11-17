package com.xiaozhi.dialogue.llm.factory;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.providers.CozeChatModel;
import com.xiaozhi.dialogue.llm.providers.DifyChatModel;
import com.xiaozhi.dialogue.llm.providers.OpenAiLlmService;
import com.xiaozhi.dialogue.token.factory.TokenServiceFactory;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * ChatModel工厂
 * 根据配置的模型ID，创建对应的ChatModel
 */
@Component
public class ChatModelFactory {
    @Autowired
    private SysConfigService configService;
    @Autowired
    private SysRoleService roleService;
    @Autowired
    private ToolCallingManager toolCallingManager;
    @Autowired
    private TokenServiceFactory tokenService;
    private final Logger logger = LoggerFactory.getLogger(ChatModelFactory.class);

    /**
     * 根据配置ID创建ChatModel，首次调用时缓存，缓存key为配置ID。
     * 
     * @see SysConfigService#selectConfigById(Integer) 已经进行了Cacheable,所以此处没有必要缓存
     * @param session 与网络链接绑定的聊天会话
     * @return
     */
    public ChatModel takeChatModel(ChatSession session) {
        SysDevice device = session.getSysDevice();
        SysRole role = roleService.selectRoleById(device.getRoleId());
        return takeChatModel(role);
    }

    public ChatModel takeChatModel(SysRole role) {
        Integer modelId = role.getModelId();
        Assert.notNull(modelId, "配置ID不能为空");
        // 根据配置ID查询配置
        SysConfig config = configService.selectConfigById(modelId);
        return createChatModel(config, role);
    }

    public ChatModel takeVisionModel() {
        SysConfig config = configService.selectModelType(SysConfig.ModelType.vision.getValue());
        Assert.notNull(config, "未配置多模态模型");
        return createChatModel(config, new SysRole());
    }

    public ChatModel takeIntentModel() {
        SysConfig config = configService.selectModelType(SysConfig.ModelType.intent.getValue());
        Assert.notNull(config, "未配置意图识别模型");
        return createChatModel(config, new SysRole());
    }

    public ChatModel takeEmbeddingModel() {
        SysConfig config = configService.selectModelType(SysConfig.ModelType.embedding.getValue());
        Assert.notNull(config, "未配置向量模型");
        return createChatModel(config, new SysRole());
    }

    /**
     * 根据角色ID创建ChatModel
     *
     * @return
     */
    public ChatModel takeChatModel(Integer roleId) {
        SysRole role = roleService.selectRoleById(roleId);
        Integer modelId = role.getModelId();
        Assert.notNull(modelId, "配置ID不能为空");
        // 根据配置ID查询配置
        SysConfig config = configService.selectConfigById(modelId);
        return createChatModel(config, role);
    }
    /**
     * 创建ChatModel
     * 
     * @param config
     * @return
     */
    private ChatModel createChatModel(SysConfig config, SysRole role) {
        String provider = config.getProvider().toLowerCase();
        String model = config.getConfigName();
        String endpoint = config.getApiUrl();
        String apiKey = config.getApiKey();
        String appId = config.getAppId();
        String apiSecret = config.getApiSecret();
        Double temperature = role.getTemperature();
        Double topP = role.getTopP();
        provider = provider.toLowerCase();
        // Coze和Dify 拥有全局唯一配置，所以需要查询唯一配置信息来作为模型的 Token 获取
        SysConfig agentConfig = new SysConfig().setConfigType("agent").setUserId(config.getUserId());
        SysConfig queryConfig;
        switch (provider) {
            case "ollama":
                return newOllamaChatModel(endpoint, appId, apiKey, apiSecret, model, temperature, topP);
            case "zhipu":
                return newZhipuChatModel(endpoint, appId, apiKey, apiSecret, model, temperature, topP);
            case "dify":
                queryConfig = configService.query(agentConfig.setProvider("dify"), null).get(0);
                return new DifyChatModel(endpoint, queryConfig.getApiKey());
            case "coze":
                queryConfig = configService.query(agentConfig.setProvider("coze"), null).get(0);
                String token = tokenService.getTokenService(queryConfig).getToken();
                return new CozeChatModel(token, model);
            // 默认为 openai 协议
            default:
                return newOpenAiChatModel(endpoint, appId, apiKey, apiSecret, model, temperature, topP);
        }
    }

    private ChatModel newOllamaChatModel(String endpoint, String appId, String apiKey, String apiSecret, String model, Double temperature, Double topP) {
        var ollamaApi = OllamaApi.builder().baseUrl(endpoint).build();

        var ollamaAiChatOptions = OllamaOptions.builder()
                .model(model)
                .temperature(temperature)
                .topP(topP)
                .build();

        var chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(ollamaAiChatOptions)
                .toolCallingManager(toolCallingManager)
                .build();
        logger.info("Using Ollama model: {}", model);
        return chatModel;
    }

    private ChatModel newOpenAiChatModel(String endpoint, String appId, String apiKey, String apiSecret, String model, Double temperature, Double topP) {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Content-Type", "application/json");

        // LM Studio不支持Http/2，所以需要强制使用HTTP/1.1
        var openAiApi = OpenAiApi.builder()
                .apiKey(StringUtils.hasText(apiKey) ? new SimpleApiKey(apiKey) : new NoopApiKey())
                .baseUrl(endpoint)
                .completionsPath("/chat/completions")
                .headers(headers)
                .webClientBuilder(WebClient.builder()
                        // Force HTTP/1.1 for streaming
                        .clientConnector(new JdkClientHttpConnector(HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(Duration.ofSeconds(30))
                                .build())))
                .restClientBuilder(RestClient.builder()
                        // Force HTTP/1.1 for non-streaming
                        .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(Duration.ofSeconds(30))
                                .build())))
                .build();
        var openAiChatOptions = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .topP(topP)
                // 注意：某些OpenAI兼容API（如豆包）可能不支持以下参数，如遇到400错误可尝试注释掉
                // .maxCompletionTokens(200)
                .maxTokens(2000)
                .streamUsage(true)
                .build();

        var chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(openAiChatOptions)
                .toolCallingManager(toolCallingManager)
                .build();
        logger.info("Using OpenAi model: {}", model);
        return chatModel;
    }

    private ChatModel newZhipuChatModel(String endpoint, String appId, String apiKey, String apiSecret, String model, Double temperature, Double topP) {
        var zhiPuAiApi = new ZhiPuAiApi(endpoint, apiKey);

        var zhipuAiChatOptions = ZhiPuAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .topP(topP)
                .build();

        var chatModel = new ZhiPuAiChatModel(zhiPuAiApi, zhipuAiChatOptions);
        logger.info("Using zhiPu model: {}", model);
        return chatModel;
    }
}