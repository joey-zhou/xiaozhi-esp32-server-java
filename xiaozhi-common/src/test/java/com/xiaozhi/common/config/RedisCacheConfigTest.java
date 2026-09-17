package com.xiaozhi.common.config;

import com.xiaozhi.common.model.bo.PermissionBO;
import com.xiaozhi.common.model.bo.UserBO;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 缓存值仍按 @class 实例化，白名单收窄后要同时钉住两头：
 * 在用的缓存形状（BO、装 BO 的容器、时间字段）读得回来，白名单外的类型 id 必须被拒。
 * 只放行 com.xiaozhi./java.util./java.time./java.math.，新增可缓存类型时这里会先红。
 */
class RedisCacheConfigTest {

    private final SerializationPair<Object> valueSerializer = valueSerializerOf("XiaoZhi:User");

    @Test
    void cachedBoKeepsTimeFieldsAcrossRoundTrip() {
        UserBO user = new UserBO();
        user.setUserId(7);
        user.setUsername("joey");
        user.setCreateTime(LocalDateTime.of(2026, 9, 5, 10, 30));

        Object restored = roundTrip(user);

        assertThat(restored).isInstanceOf(UserBO.class).isEqualTo(user);
    }

    @Test
    void cachedListOfBoKeepsElementTypes() {
        PermissionBO permission = new PermissionBO();
        permission.setPermissionId(1);
        permission.setPermissionKey("system:config:api:list");
        List<PermissionBO> permissions = new ArrayList<>(List.of(permission));

        Object restored = roundTrip(permissions);

        assertThat(restored).isInstanceOf(ArrayList.class).isEqualTo(permissions);
    }

    @Test
    void cachedStringSetKeepsOrder() {
        LinkedHashSet<String> excludedTools = new LinkedHashSet<>(List.of("get_time", "play_music"));

        Object restored = roundTrip(excludedTools);

        assertThat(restored).isInstanceOf(LinkedHashSet.class).isEqualTo(excludedTools);
    }

    @Test
    void typeOutsideAllowListIsRejected() {
        ByteBuffer forgedPayload = ByteBuffer.wrap("[\"java.io.File\",\"/tmp/x\"]".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> valueSerializer.read(forgedPayload))
            .isInstanceOf(SerializationException.class)
            .hasMessageContaining("java.io.File")
            .hasMessageContaining("denied resolution");
    }

    private Object roundTrip(Object value) {
        return valueSerializer.read(valueSerializer.write(value));
    }

    /** 缓存名走的是同一个值序列化器，取任意一个缓存即可拿到它。 */
    private static SerializationPair<Object> valueSerializerOf(String cacheName) {
        Cache cache = new RedisCacheConfig().cacheManager(mock(RedisConnectionFactory.class)).getCache(cacheName);
        RedisCache redisCache = (RedisCache) (cache instanceof TransactionAwareCacheDecorator decorator
            ? decorator.getTargetCache()
            : cache);
        return redisCache.getCacheConfiguration().getValueSerializationPair();
    }
}
