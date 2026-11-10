package com.xiaozhi.ai.token;

import com.xiaozhi.ai.token.provider.TokenProvider;
import com.xiaozhi.event.AiConfigChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * token 按 provider:configId 缓存。配置改了 provider 之后再按新 provider 删，旧 provider 下的 token 删不掉，
 * 所以按 configId 把每个 provider 下的都删
 */
class TokenServiceRemoveCacheTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        TokenProvider aliyun = mock(TokenProvider.class);
        when(aliyun.getSupportedProviders()).thenReturn(Set.of("aliyun-nls"));
        TokenProvider coze = mock(TokenProvider.class);
        when(coze.getSupportedProviders()).thenReturn(Set.of("coze"));
        tokenService = new TokenService(List.of(aliyun, coze));
        tokenService.init();
        ReflectionTestUtils.setField(tokenService, "stringRedisTemplate", redis);
    }

    @Test
    void removesTheConfigsTokenUnderEveryProvider() {
        tokenService.removeCache(7);

        assertThat(deletedKeys()).containsExactlyInAnyOrder("xiaozhi:token:aliyun-nls:7", "xiaozhi:token:coze:7");
    }

    @Test
    void anyLocalConfigChangeDropsItsToken() {
        tokenService.onAiConfigChanged(new AiConfigChangedEvent(this, "agent", 9));

        assertThat(deletedKeys()).contains("xiaozhi:token:coze:9");
    }

    @SuppressWarnings("unchecked")
    private Collection<String> deletedKeys() {
        ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.forClass(Collection.class);
        verify(redis).delete(keys.capture());
        return keys.getValue();
    }
}
