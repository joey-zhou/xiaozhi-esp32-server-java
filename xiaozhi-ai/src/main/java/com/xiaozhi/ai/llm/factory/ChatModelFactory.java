package com.xiaozhi.ai.llm.factory;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.port.ConfigLookup;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ChatModel
 * 
 * 设计模式: 策略模式 + 工厂模式
 * - 通过ChatModelProvider接口定义统一的创建策略
 * - 每个LLM提供商实现独立的Provider
 * - 工厂类通过Spring自动注入所有Provider,自动路由到对应实现
 */
@Component
public class ChatModelFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatModelFactory.class);
    
    @Autowired
    private ConfigLookup configLookup;
    
    /**
     * 所有的ChatModel提供者,Spring会自动注入所有实现了ChatModelProvider接口的Bean
     */
    private final Map<String, ChatModelProvider> providers;

    @Autowired
    private ObservationRegistry registry;
    /**
     * 构造函数,自动注入所有ChatModelProvider
     * @param providers 所有的Provider实现
     */
    @Autowired
    public ChatModelFactory(List<ChatModelProvider> providers) {
        // 将Provider列表转换为Map,key为provider名称(小写),value为Provider实例
        this.providers = providers.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderName().toLowerCase(),
                        Function.identity()
                ));
    }
    
    public ChatModel getChatModel(RoleBO role) {
        RoleBO effectiveRole = role != null ? role : new RoleBO();
        Integer modelId = effectiveRole.getModelId();
        Assert.notNull(modelId, "配置ID不能为空");
        // 根据配置ID查询配置
        ConfigBO config = configLookup.getConfig(modelId);
        return createChatModel(config, effectiveRole);
    }

    public ChatModel getVisionModel() {
        ConfigBO config = configLookup.getDefaultConfig("llm", ConfigBO.ModelType.vision.getValue());
        Assert.notNull(config, "未配置多模态模型");
        return createChatModel(config, new RoleBO());
    }

    public ChatModel getIntentModel() {
        ConfigBO config = configLookup.getDefaultConfig("llm", ConfigBO.ModelType.intent.getValue());
        Assert.notNull(config, "未配置意图识别模型");
        return createChatModel(config, new RoleBO());
    }

    public EmbeddingModel getEmbeddingModel(Integer configId) {
        Assert.notNull(configId, "配置ID不能为空");
        ConfigBO config = configLookup.getConfig(configId);
        Assert.notNull(config, "未找到配置, configId=" + configId);
        return getEmbeddingModel(config);
    }

    public EmbeddingModel getEmbeddingModel(ConfigBO config) {
        Assert.notNull(config, "未配置向量模型");
        String providerName = config.getProvider().toLowerCase();
        ChatModelProvider provider = providers.get(providerName);
        if (provider != null) {
            return provider.createEmbeddingModel(config);
        }
        provider = providers.get("openai");
        if (provider != null) {
            return provider.createEmbeddingModel(config);
        }
        throw new IllegalArgumentException(
                String.format("不支持的Provider: %s, 可用的Providers: %s", providerName, providers.keySet()));
    }

    /**
     * 创建ChatModel
     *
     * @param config 模型配置
     * @param role 角色配置
     * @return ChatModel实例
     */
    private ChatModel createChatModel(ConfigBO config, RoleBO role) {
        String providerName = config.getProvider().toLowerCase();
        
        // 从providers Map中获取对应的Provider
        ChatModelProvider provider = providers.get(providerName);
        
        if (provider != null) {
            return provider.createChatModel(config, role);
        }
        
        // 如果没有找到对应的Provider,尝试使用OpenAI Provider作为默认(兼容OpenAI协议)
        provider = providers.get("openai");
        
        if (provider != null) {
            return provider.createChatModel(config, role);
        }
        
        // 如果连OpenAI Provider都没有,抛出异常
        throw new IllegalArgumentException(
                String.format("不支持的Provider: %s, 可用的Providers: %s", 
                        providerName, 
                        providers.keySet())
        );
    }
}
