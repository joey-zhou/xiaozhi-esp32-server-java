package com.xiaozhi.ai.llm.factory;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * ChatModel提供者接口
 * 每个LLM提供商实现此接口以创建自己的ChatModel
 */
public interface ChatModelProvider {

    /**
     * 获取提供商名称(小写)
     * @return 提供商名称,如: openai, ollama, zhipu, dify, xingchen, coze, xinghuo
     */
    String getProviderName();

    /**
     * 创建ChatModel实例
     * @param config 模型配置
     * @param role 角色配置
     * @return ChatModel实例
     */
    ChatModel createChatModel(ConfigBO config, RoleBO role);

    /**
     * 创建EmbeddingModel实例，不支持的提供商直接抛出异常
     * @param config 模型配置
     * @return EmbeddingModel实例
     */
    default EmbeddingModel createEmbeddingModel(ConfigBO config) {
        throw new UnsupportedOperationException(getProviderName() + " 不支持 Embedding 模型");
    }

    /**
     * 是否支持该提供商
     * @param provider 提供商名称(小写)
     * @return true表示支持
     */
    default boolean supports(String provider) {
        return getProviderName().equalsIgnoreCase(provider);
    }
}

