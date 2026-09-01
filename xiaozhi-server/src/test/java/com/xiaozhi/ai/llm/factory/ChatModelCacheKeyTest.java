package com.xiaozhi.ai.llm.factory;

import com.xiaozhi.common.model.bo.RoleBO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * temperature/topP 烘焙在 ChatModel 的 defaultOptions 里，
 * 不进缓存 key 就会出现改了角色参数不生效。
 */
class ChatModelCacheKeyTest {

    private RoleBO role(Double temperature, Double topP) {
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

    @Test
    void keyIsPrefixedByModelIdWithoutAmbiguity() {
        // removeCache 用 configId + ":" 前缀匹配，11 不能被 1 误删
        assertThat(ChatModelFactory.cacheKey(11, role(0.7d, 0.9d))).doesNotStartWith("1:");
    }
}
