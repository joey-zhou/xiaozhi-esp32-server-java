package com.xiaozhi.config.infrastructure;

import com.xiaozhi.common.config.CacheNames;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.config.dal.mysql.dataobject.ConfigDO;
import com.xiaozhi.config.dal.mysql.mapper.ConfigMapper;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.infrastructure.convert.ConfigConverter;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住配置写入会把默认配置的缓存值与「没有默认配置」标记一起淘汰。
 * <p>
 * 两者都在 Redis 里，server 进程写入后 dialogue 进程下一次查询就能拿到新配置，不依赖广播。
 * llm 配置的 modelType 可以被改掉，所以 llm 配置写入时按全部 modelType 淘汰。
 */
@ExtendWith(MockitoExtension.class)
class ConfigRepositoryAbsenceMarkerTest {

    @Mock
    private ConfigMapper configMapper;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ConfigRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(ConfigDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ConfigRepositoryImpl();
        ReflectionTestUtils.setField(repository, "configMapper", configMapper);
        ReflectionTestUtils.setField(repository, "configConverter", new ConfigConverter());
        ReflectionTestUtils.setField(repository, "cacheManager", cacheManager);
        ReflectionTestUtils.setField(repository, "eventPublisher", eventPublisher);
        when(cacheManager.getCache(CacheNames.SYS_CONFIG)).thenReturn(cache);
        when(configMapper.insert(any(ConfigDO.class))).thenAnswer(invocation -> {
            ConfigDO inserted = invocation.getArgument(0);
            inserted.setConfigId(21);
            return 1;
        });
    }

    @Test
    void savingLlmConfigEvictsDefaultValueAndAbsentMarkerOfEveryModelType() {
        repository.save(AiConfig.newConfig(7, new ConfigBO()
            .setConfigType("llm")
            .setModelType("embedding")
            .setProvider("openai")
            .setConfigName("embedding")));

        for (String key : List.of("default:llm", "default:llm:chat", "default:llm:vision",
                "default:llm:intent", "default:llm:embedding")) {
            verify(cache).evictIfPresent(key);
            verify(cache).evictIfPresent("absent:" + key);
        }
    }

    @Test
    void savingOssConfigOnlyEvictsItsOwnDefaultKeys() {
        repository.save(AiConfig.newConfig(7, new ConfigBO()
            .setConfigType("oss")
            .setProvider("local")
            .setConfigName("local")));

        verify(cache).evictIfPresent("default:oss");
        verify(cache).evictIfPresent("absent:default:oss");
        verify(cache, never()).evictIfPresent("absent:default:llm:embedding");
    }
}
