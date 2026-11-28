package com.xiaozhi.ai.token;

import com.xiaozhi.ai.token.provider.TokenProvider;
import com.xiaozhi.common.DistributedLocks;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.utils.DateUtils;
import com.xiaozhi.utils.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 同一份配置只有一个实例去换 token；手里的还没过期就不排队等别人刷新 */
class TokenServiceGetTokenTest {

    private static final String KEY = "xiaozhi:token:coze:7";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final TokenProvider coze = mock(TokenProvider.class);
    private final ConfigBO config = new ConfigBO().setProvider("coze").setConfigId(7);
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        when(coze.getSupportedProviders()).thenReturn(Set.of("coze"));
        tokenService = new TokenService(List.of(coze));
        tokenService.init();
        ReflectionTestUtils.setField(tokenService, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(tokenService, "distributedLocks", new DistributedLocks(redissonClient));
        when(redis.opsForValue()).thenReturn(values);
        when(redissonClient.getLock("xiaozhi:lock:token:coze:7")).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    private static String cached(Duration validFor) {
        return JsonUtil.toJson(new TokenCache("old", DateUtils.millis() + validFor.toMillis()));
    }

    @Test
    void aFreshCachedTokenIsUsedWithoutLocking() {
        when(values.get(KEY)).thenReturn(cached(Duration.ofHours(5)));

        assertThat(tokenService.getToken(config)).isEqualTo("old");

        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    void theLockHolderFetchesAndCaches() throws InterruptedException {
        when(lock.tryLock(4000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(coze.fetchToken(config)).thenReturn(new TokenCache("new", DateUtils.millis() + Duration.ofHours(5).toMillis()));

        assertThat(tokenService.getToken(config)).isEqualTo("new");

        verify(values).set(eq(KEY), anyString(), any(Duration.class));
        verify(lock).unlock();
    }

    @Test
    void aTokenAboutToExpireIsStillUsedWhileAnotherInstanceRefreshes() throws InterruptedException {
        when(values.get(KEY)).thenReturn(cached(Duration.ofMinutes(10)));
        when(lock.tryLock(0, TimeUnit.MILLISECONDS)).thenReturn(false);

        assertThat(tokenService.getToken(config)).isEqualTo("old");

        verify(coze, never()).fetchToken(any());
    }

    @Test
    void withoutAnyTokenABusyLockFails() throws InterruptedException {
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);

        assertThatThrownBy(() -> tokenService.getToken(config)).isInstanceOf(IllegalStateException.class);
    }
}
