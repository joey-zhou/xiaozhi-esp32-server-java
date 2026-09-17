package com.xiaozhi.config.infrastructure;

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
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 钉住 save() 把本次写库的时间戳回填进聚合根。
 * <p>createTime / updateTime 由 MyBatis-Plus 的自动填充在写库时塞进 DO，配置的写接口靠这次回填
 * 直接出参；一旦不回填，创建配置的返回里两个时间就会变成 null，只能再查一遍配置表补回来。
 */
@ExtendWith(MockitoExtension.class)
class ConfigRepositoryTimestampTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 8, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 9, 5, 12, 0);

    @Mock
    private ConfigMapper configMapper;

    @Mock
    private CacheManager cacheManager;

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
    }

    @Test
    void insertWritesBackBothTimestampsAndTheGeneratedId() {
        when(configMapper.insert(any(ConfigDO.class))).thenAnswer(invocation -> {
            ConfigDO inserted = invocation.getArgument(0);
            inserted.setConfigId(11);
            inserted.setCreateTime(CREATED_AT);
            inserted.setUpdateTime(CREATED_AT);
            return 1;
        });

        AiConfig config = AiConfig.newConfig(7, new ConfigBO()
            .setConfigType("llm")
            .setProvider("aliyun")
            .setConfigName("通义千问"));
        repository.save(config);

        assertThat(config.getConfigId()).isEqualTo(11);
        assertThat(config.getCreateTime()).isEqualTo(CREATED_AT);
        assertThat(config.getUpdateTime()).isEqualTo(CREATED_AT);
    }

    @Test
    void updateWritesBackNewUpdateTimeAndKeepsCreateTime() {
        when(configMapper.updateById(any(ConfigDO.class))).thenAnswer(invocation -> {
            ConfigDO updated = invocation.getArgument(0);
            updated.setUpdateTime(UPDATED_AT);
            return 1;
        });

        AiConfig config = AiConfig.reconstitute(11, 7, "llm", "aliyun", "通义千问", null, "chat",
            null, "key", null, null, null, null, null, null,
            AiConfig.STATE_ENABLED, false, CREATED_AT, LocalDateTime.of(2026, 2, 1, 8, 0));
        config.update(new ConfigBO().setConfigName("改过的名字"));
        repository.save(config);

        // 更新语句不带 createTime，回填时不能把聚合根上的创建时间清掉
        assertThat(config.getCreateTime()).isEqualTo(CREATED_AT);
        assertThat(config.getUpdateTime()).isEqualTo(UPDATED_AT);
    }
}
