package com.xiaozhi.role.domain.vo;

/**
 * LLM 模型配置值对象。
 * <p>采样参数的默认值由本值对象持有：新建角色没填、库里该列为 NULL 的历史行，两条路径都经
 * {@link #withDefaults()} 补齐，取到的是同一份默认。
 */
public record LlmConfig(Integer modelId, Double temperature, Double topP) {

    /** 采样温度默认值 */
    public static final Double DEFAULT_TEMPERATURE = 0.7d;

    /** 核采样 topP 默认值 */
    public static final Double DEFAULT_TOP_P = 0.9d;

    public static LlmConfig defaults() {
        return new LlmConfig(null, DEFAULT_TEMPERATURE, DEFAULT_TOP_P);
    }

    /** 补齐没给值的采样参数 */
    public LlmConfig withDefaults() {
        return new LlmConfig(modelId,
                temperature != null ? temperature : DEFAULT_TEMPERATURE,
                topP != null ? topP : DEFAULT_TOP_P);
    }

    /** 按 patch 合并：patch 没给的字段保留当前值，与「局部更新」语义一致 */
    public LlmConfig merge(LlmConfig patch) {
        if (patch == null) {
            return this;
        }
        return new LlmConfig(
                patch.modelId() != null ? patch.modelId() : modelId,
                patch.temperature() != null ? patch.temperature() : temperature,
                patch.topP() != null ? patch.topP() : topP);
    }
}
