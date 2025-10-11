package com.xiaozhi.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

@Configuration
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // 默认缓存配置（60 秒过期）
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // 过期时间,默认十分钟
                .entryTtl(Duration.ofSeconds(600))
                // 不缓存 null 值
                .disableCachingNullValues();

        // 创建 RedisCacheManager
        return RedisCacheManager.builder(redisConnectionFactory)
                // 默认配置
                .cacheDefaults(defaultConfig)
                .build();
    }
}
