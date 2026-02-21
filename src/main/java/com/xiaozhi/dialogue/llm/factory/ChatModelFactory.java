package com.xiaozhi.dialogue.llm.factory;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysRoleService;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
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
    private SysConfigService configService;
    
    @Autowired
    private SysRoleService roleService;
    
    /**
     * 所有的ChatModel提供者,Spring会自动注入所有实现了ChatModelProvider接口的Bean
     */
    private final Map<String, ChatModelProvider> providers;

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
    
    /**
     * 根据配置ID创建ChatModel，首次调用时缓存，缓存key为配置ID。
     * 
     * @see SysConfigService#selectConfigById(Integer) 已经进行了Cacheable,所以此处没有必要缓存
     * @param session 与网络链接绑定的聊天会话
     * @return ChatModel实例
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

    /**
     * 根据角色ID创建ChatModel
     * @param roleId 角色ID
     * @return ChatModel实例
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
     * @param config 模型配置
     * @param role 角色配置
     * @return ChatModel实例
     */
    private ChatModel createChatModel(SysConfig config, SysRole role) {
        String providerName = config.getProvider().toLowerCase();
        
        // 从providers Map中获取对应的Provider
        ChatModelProvider provider = providers.get(providerName);
        
        if (provider != null) {
            logger.debug("使用Provider [{}] 创建ChatModel", providerName);
            return provider.createChatModel(config, role);
        }
        
        // 如果没有找到对应的Provider,尝试使用OpenAI Provider作为默认(兼容OpenAI协议)
        logger.warn("未找到Provider [{}],使用OpenAI协议作为默认Provider", providerName);
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