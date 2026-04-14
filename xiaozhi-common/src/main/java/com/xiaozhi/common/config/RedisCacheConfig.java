package com.xiaozhi.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis缓存配置
 * <p>
 * 防雪崩策略：每个缓存名的 TTL = 基础时长 + 随机偏移（基础时长的 10%，最多 1 小时）。
 * 随机值在每个 JVM 实例启动时独立生成，多实例部署时同类 key 的 TTL 自然错开。
 *
 * @author Joey
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /** 随机偏移上限（秒） */
    private static final int MAX_JITTER_SECONDS = 3600;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer serializer = createSerializer();

        // 默认配置: 1天 + 随机偏移
        RedisCacheConfiguration defaultConfig = buildConfig(serializer, Duration.ofDays(1));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("XiaoZhi:Device",        buildConfig(serializer, Duration.ofDays(1)));
        cacheConfigurations.put("XiaoZhi:Permission",    buildConfig(serializer, Duration.ofDays(7)));
        cacheConfigurations.put("XiaoZhi:User",          buildConfig(serializer, Duration.ofDays(1)));
        cacheConfigurations.put("XiaoZhi:SysConfig",     buildConfig(serializer, Duration.ofDays(7)));
        cacheConfigurations.put("XiaoZhi:McpToolExclude",buildConfig(serializer, Duration.ofDays(7)));

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();
    }

    /**
     * 构建缓存配置，TTL = baseTtl + 随机偏移。
     * 偏移量 = min(baseTtl 的 10%, MAX_JITTER_SECONDS) 范围内的随机秒数。
     */
    private RedisCacheConfiguration buildConfig(GenericJackson2JsonRedisSerializer serializer, Duration baseTtl) {
        long jitterBound = Math.min(baseTtl.toSeconds() / 10, MAX_JITTER_SECONDS);
        long jitterSeconds = jitterBound > 0 ? ThreadLocalRandom.current().nextLong(jitterBound) : 0;

        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(baseTtl.plusSeconds(jitterSeconds))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
            .disableCachingNullValues();
    }

    private GenericJackson2JsonRedisSerializer createSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
