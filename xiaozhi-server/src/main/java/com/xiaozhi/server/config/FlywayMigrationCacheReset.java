package com.xiaozhi.server.config;

import com.xiaozhi.common.CacheHelper;
import com.xiaozhi.common.config.CacheNames;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 迁移会补权限、改授权与默认配置，缓存里的旧值要等过期（权限 7 天），这期间新加的功能对所有人 403。
 * 本次启动真的执行了迁移，就在启动完成后清掉这几份缓存；Redis 两个进程共用，清一次都生效。
 */
@Slf4j
@Component
public class FlywayMigrationCacheReset implements FlywayMigrationStrategy {

    private static final List<String> MIGRATION_AFFECTED_CACHES = List.of(CacheNames.PERMISSION, CacheNames.SYS_CONFIG);

    /** 迁移在数据源初始化阶段就跑，那时不去碰缓存，启动完成后再取 */
    private final ObjectProvider<CacheManager> cacheManager;

    private volatile int migrationsExecuted;

    public FlywayMigrationCacheReset(ObjectProvider<CacheManager> cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void migrate(Flyway flyway) {
        migrationsExecuted = flyway.migrate().migrationsExecuted;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resetCachesAfterMigration() {
        if (migrationsExecuted == 0) {
            return;
        }
        CacheManager manager = cacheManager.getObject();
        MIGRATION_AFFECTED_CACHES.forEach(name -> CacheHelper.clearNow(manager.getCache(name)));
        log.info("本次启动执行了 {} 个迁移，已清空缓存 {}", migrationsExecuted, MIGRATION_AFFECTED_CACHES);
    }
}
