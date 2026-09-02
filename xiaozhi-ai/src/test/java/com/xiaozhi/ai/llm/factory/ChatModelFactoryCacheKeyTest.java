package com.xiaozhi.ai.llm.factory;

import com.xiaozhi.common.model.bo.RoleBO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * temperature/topP 烘焙在 ChatModel 的 defaultOptions 里，
 * 不进缓存 key 就会出现改了角色参数不生效。
 */
class ChatModelFactoryCacheKeyTest {

    private static RoleBO role(Double temperature, Double topP) {
        RoleBO role = new RoleBO();
        role.setTemperature(temperature);
        role.setTopP(topP);
        return role;
    }

    @Test
    void differentTemperatureYieldsDifferentKey() {
        assertThat(ChatModelFactory.cacheKey(1, role(0.7d, 0.9d)))
                .isNotEqualTo(ChatModelFactory.cacheKey(1, role(0.9d, 0.9d)));
    }

    @Test
    void differentTopPYieldsDifferentKey() {
        assertThat(ChatModelFactory.cacheKey(1, role(0.7d, 0.9d)))
                .isNotEqualTo(ChatModelFactory.cacheKey(1, role(0.7d, 0.5d)));
    }

    @Test
    void differentModelIdYieldsDifferentKey() {
        assertThat(ChatModelFactory.cacheKey(1, role(0.7d, 0.9d)))
                .isNotEqualTo(ChatModelFactory.cacheKey(2, role(0.7d, 0.9d)));
    }

    @Test
    void sameConfigYieldsSameKey() {
        assertThat(ChatModelFactory.cacheKey(1, role(0.7d, 0.9d)))
                .isEqualTo(ChatModelFactory.cacheKey(1, role(0.7d, 0.9d)));
    }

    // removeCache 用 configId + ":" 前缀匹配，key 必须以 modelId + ":" 开头，11 才不会被 removeCache(1) 误删
    @Test
    void keyStartsWithModelIdFollowedByColon() {
        assertThat(ChatModelFactory.cacheKey(11, role(0.7d, 0.9d)))
                .startsWith("11:")
                .doesNotStartWith("1:");
    }
}
