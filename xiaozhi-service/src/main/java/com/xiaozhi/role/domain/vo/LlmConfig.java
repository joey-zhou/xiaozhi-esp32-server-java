package com.xiaozhi.role.domain.vo;

/**
 * LLM 模型配置值对象。
 */
public record LlmConfig(Integer modelId, Double temperature, Double topP) {

    public static LlmConfig defaults() {
        return new LlmConfig(null, 0.7d, 0.9d);
    }

}
