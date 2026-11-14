package com.xiaozhi.server.config;

import com.xiaozhi.common.config.CacheNames;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 迁移会补权限、改授权与默认配置，缓存里的旧值要等过期（权限 7 天），这期间新功能对所有人 403。
 * 本次启动真的执行了迁移才清，没迁移时重启不该把缓存冲掉。
 */
class FlywayMigrationCacheResetTest {

    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache permissionCache = mock(Cache.class);
    private final Cache configCache = mock(Cache.class);
    private final FlywayMigrationCacheReset reset = new FlywayMigrationCacheReset(provider(cacheManager));

    @Test
    void clearsPermissionAndConfigCachesWhenMigrationsRan() {
        when(cacheManager.getCache(CacheNames.PERMISSION)).thenReturn(permissionCache);
        when(cacheManager.getCache(CacheNames.SYS_CONFIG)).thenReturn(configCache);

        reset.migrate(flywayExecuting(2));
        reset.resetCachesAfterMigration();

        verify(permissionCache).invalidate();
        verify(configCache).invalidate();
    }

    @Test
    void leavesCachesAloneWhenNothingWasMigrated() {
        reset.migrate(flywayExecuting(0));
        reset.resetCachesAfterMigration();

        verifyNoInteractions(cacheManager);
        verify(permissionCache, never()).invalidate();
    }

    private static Flyway flywayExecuting(int migrations) {
        MigrateResult result = new MigrateResult("11", "xiaozhi", "xiaozhi", "MySQL");
        result.migrationsExecuted = migrations;
        Flyway flyway = mock(Flyway.class);
        when(flyway.migrate()).thenReturn(result);
        return flyway;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<CacheManager> provider(CacheManager cacheManager) {
        ObjectProvider<CacheManager> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(cacheManager);
        return provider;
    }
}
