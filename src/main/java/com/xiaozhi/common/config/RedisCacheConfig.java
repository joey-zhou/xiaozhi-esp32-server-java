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
 * 防雪崩由随机TTL负责
 *
 * @author Joey
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer serializer = createSerializer();

        // 默认配置: 1天 + 随机1小时, 禁止缓存null
        RedisCacheConfiguration defaultConfig = buildConfig(serializer, Duration.ofDays(1));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        cacheConfigurations.put("XiaoZhi:SysUser",     buildConfig(serializer, Duration.ofDays(365)));
        cacheConfigurations.put("XiaoZhi:SysDevice",   buildConfig(serializer, Duration.ofDays(1)));
        cacheConfigurations.put("XiaoZhi:SysConfig",   buildConfig(serializer, Duration.ofDays(7)));
        cacheConfigurations.put("XiaoZhi:SysRole",     buildConfig(serializer, Duration.ofDays(7)));
        cacheConfigurations.put("XiaoZhi:SysMcpServer",buildConfig(serializer, Duration.ofDays(7)));
        cacheConfigurations.put("XiaoZhi:McpToolExclude", buildConfig(serializer, Duration.ofDays(7)));
        cacheConfigurations.put("monitoring",           buildConfig(serializer, Duration.ofHours(1)));

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();
    }

    private RedisCacheConfiguration buildConfig(GenericJackson2JsonRedisSerializer serializer, Duration ttl) {
        long randomSeconds = ThreadLocalRandom.current().nextInt(3600);
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(ttl.plusSeconds(randomSeconds))
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
