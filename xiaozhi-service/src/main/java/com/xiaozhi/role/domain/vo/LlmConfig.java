package com.xiaozhi.role.domain.vo;

import com.xiaozhi.common.model.bo.RoleBO;

/**
 * LLM 模型配置值对象。
 * <p>采样参数的默认值定义在 {@link RoleBO}：新建角色没填、库里该列为 NULL 的历史行，两条路径都经
 * {@link #withDefaults()} 补齐，取到的是同一份默认。
 */
public record LlmConfig(Integer modelId, Double temperature, Double topP) {



    public static LlmConfig defaults() {
        return new LlmConfig(null, RoleBO.DEFAULT_TEMPERATURE, RoleBO.DEFAULT_TOP_P);
    }

    /** 补齐没给值的采样参数 */
    public LlmConfig withDefaults() {
        return new LlmConfig(modelId,
                temperature != null ? temperature : RoleBO.DEFAULT_TEMPERATURE,
                topP != null ? topP : RoleBO.DEFAULT_TOP_P);
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
