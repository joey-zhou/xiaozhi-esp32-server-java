package com.xiaozhi.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 客户端。只服务分布式锁（CacheHelper、各定时任务）与布隆过滤器，
 * 普通 Redis 操作（缓存、Pub/Sub、设备注册）走 Spring Boot 装配的 Lettuce 连接池。
 * <p>
 * 这两件事必须分开，是因为 redisson-spring-boot-starter 的自动配置会顺带提供一个
 * {@code RedissonConnectionFactory} 作为 {@code RedisConnectionFactory}，并且排在
 * {@code RedisAutoConfiguration} 之前——一旦让它生效，Lettuce 那份连接池配置就成了摆设，
 * 所有 Redis 命令还要经过 redisson-spring-data 适配器（本仓 pom 里锁 spring-data 版本
 * 就是被这个适配器逼的）。
 * RedissonClient 改由本类按需构建。
 */
@Configuration
@EnableConfigurationProperties(RedisProperties.class)
public class RedissonConfig {

    /** 锁与布隆过滤器的并发量远小于普通缓存读写，连接池不需要跟 Lettuce 一样大 */
    private static final int CONNECTION_POOL_SIZE = 8;
    private static final int MINIMUM_IDLE_SIZE = 2;
    private static final int SUBSCRIPTION_POOL_SIZE = 2;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        config.setCodec(new JsonJacksonCodec());

        String scheme = redisProperties.getSsl() != null && redisProperties.getSsl().isEnabled()
                ? "rediss://" : "redis://";
        SingleServerConfig server = config.useSingleServer()
                .setAddress(scheme + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setDatabase(redisProperties.getDatabase())
                .setConnectionPoolSize(CONNECTION_POOL_SIZE)
                .setConnectionMinimumIdleSize(MINIMUM_IDLE_SIZE)
                .setSubscriptionConnectionPoolSize(SUBSCRIPTION_POOL_SIZE)
                .setSubscriptionConnectionMinimumIdleSize(1);

        // 口令为空时不能调 setPassword("")：Redisson 会照发 AUTH ""，没开 requirepass 的 Redis 直接拒连
        if (StringUtils.hasText(redisProperties.getUsername())) {
            server.setUsername(redisProperties.getUsername());
        }
        if (StringUtils.hasText(redisProperties.getPassword())) {
            server.setPassword(redisProperties.getPassword());
        }
        if (redisProperties.getConnectTimeout() != null) {
            server.setConnectTimeout((int) redisProperties.getConnectTimeout().toMillis());
        }
        if (redisProperties.getTimeout() != null) {
            server.setTimeout((int) redisProperties.getTimeout().toMillis());
        }

        return Redisson.create(config);
    }
}
