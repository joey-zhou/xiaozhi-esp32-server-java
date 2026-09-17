package com.xiaozhi.common;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
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

    @Resource
    private RedissonClient redissonClient;

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
        // 1. 先尝试从缓存获取
        T cached = cacheGetter.get();
        if (cached != null) {
            return cached;
        }

        // 2. 缓存未命中,使用分布式锁
        RLock lock = redissonClient.getLock("lock:" + lockKey);
        boolean locked;
        try {
            // 尝试获取锁,最多等待3秒；不传租期，交给 Redisson 看门狗按持锁线程存活自动续期，
            // 避免固定租期短于查库耗时（如 Hikari 连接池排队）导致锁提前失效
            locked = lock.tryLock(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取锁被中断: {}", lockKey, e);
            // 降级: 直接查询数据库
            return dbGetter.get();
        }

        if (!locked) {
            // 获取锁失败,直接查询数据库(降级策略)
            log.warn("获取锁超时,直接查询数据库: {}", lockKey);
            return dbGetter.get();
        }

        // 锁只包获取/释放本身，dbGetter 的异常原样上抛，不在这里当成锁异常吞掉再查一次
        try {
            // 3. 双重检查,避免重复查询数据库
            cached = cacheGetter.get();
            if (cached != null) {
                log.debug("获取锁后从缓存命中: {}", lockKey);
                return cached;
            }

            // 4. 查询数据库
            log.debug("从数据库查询: {}", lockKey);
            return dbGetter.get();
        } finally {
            try {
                lock.unlock();
            } catch (IllegalMonitorStateException e) {
                // 业务耗时超过看门狗续期窗口导致锁已自动释放时会抛出，此时锁已不在自己手里，忽略即可
                log.warn("释放锁时锁已失效: {}", lockKey);
            }
        }
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

}
