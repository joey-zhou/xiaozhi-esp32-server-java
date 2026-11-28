package com.xiaozhi.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 按键计数的时间窗口，窗口从第一次计数起算。
 * 自增与设过期在一次 Redis 调用里完成：分两步时进程在中间挂掉会留下永不过期的键，把该主体永久锁死；
 * TTL 为负（历史遗留或丢了过期时间）时顺带补回。
 */
@Component
public class WindowCounterRegistry {

    private static final RedisScript<Long> INCREMENT_IN_WINDOW = RedisScript.of(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 or redis.call('TTL', KEYS[1]) < 0 then "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return count",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public WindowCounterRegistry(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** @return 本窗口内的计数（含这一次） */
    public long increment(String key, Duration window) {
        Long count = stringRedisTemplate.execute(INCREMENT_IN_WINDOW, List.of(key), String.valueOf(window.toSeconds()));
        if (count == null) {
            throw new IllegalStateException("Redis 计数没有返回结果: " + key);
        }
        return count;
    }
}
