package com.xiaozhi.common;

import org.springframework.cache.Cache;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
/**
 * 缓存助手类
 * 提供带分布式锁的缓存查询,防止缓存击穿
 *
 * @author Joey
 */
@Slf4j
@Component
public class CacheHelper {

    private static final Duration LOCK_WAIT = Duration.ofSeconds(3);

    @Resource
    private DistributedLocks distributedLocks;

    /**
     * 带分布式锁的缓存查询
     * 防止缓存击穿 - 当缓存失效时,只有一个请求去查询数据库
     *
     * @param lockKey 锁的key
     * @param cacheGetter 从缓存获取数据的函数
     * @param dbGetter 从数据库获取数据的函数
     * @param <T> 数据类型
     * @return 数据
     */
    public <T> T getWithLock(String lockKey, Supplier<T> cacheGetter, Supplier<T> dbGetter) {
        T cached = cacheGetter.get();
        if (cached != null) {
            return cached;
        }
        // 等不到锁就直接查库降级；拿到锁后再看一次缓存，避免重复查库
        return distributedLocks.callWithin("cache:" + lockKey, LOCK_WAIT, () -> {
            T filled = cacheGetter.get();
            return filled != null ? filled : dbGetter.get();
        }, () -> {
            log.warn("获取锁超时,直接查询数据库: {}", lockKey);
            return dbGetter.get();
        });
    }

    /**
     * 写路径的缓存淘汰：先立刻淘汰一次，再登记一次事务提交后的淘汰。
     * <p>
     * CacheManager 开了 transactionAware，单调 {@code evict} 会被推迟到事务提交后才真正执行，
     * 同一个事务里写完紧接着回读就会命中没淘汰掉的旧值，接口返回给前端的是改动前的数据。
     * {@code evictIfPresent} 不走这层推迟，直接打到底层缓存，先解决「本次事务内读到旧值」。
     * <p>
     * 提交后的那一次不能省：事务执行期间的并发读会把旧值重新回填，只淘汰一次的话旧值会一直留到自然过期。
     *
     * @param cache 缓存实例，为 null 时什么都不做
     * @param key   缓存键
     */
    public static void evictNow(Cache cache, Object key) {
        if (cache == null || key == null) {
            return;
        }
        cache.evictIfPresent(key);
        cache.evict(key);
    }

    /** 整份清空，时机同 {@link #evictNow}：立刻清一次，再登记一次事务提交后的清空 */
    public static void clearNow(Cache cache) {
        if (cache == null) {
            return;
        }
        cache.invalidate();
        cache.clear();
    }

}
