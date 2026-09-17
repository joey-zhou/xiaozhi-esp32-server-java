package com.xiaozhi.config.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 这些断言须与 {@code sys_config.uk_config_default} 唯一索引的键逐条对齐。 */
@ExtendWith(MockitoExtension.class)
class ConfigRepositoryDefaultScopeTest {

    private static final Integer USER_ID = 42;

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
        // resetDefault 现在先查出被降级的旧默认，再按 id 批量更新；这里给一条待降级记录，让 update 断言仍能触发
        ConfigDO downgraded = new ConfigDO();
        downgraded.setConfigId(1);
        when(configMapper.selectList(any())).thenReturn(List.of(downgraded));
    }

    @Test
    void resetDefaultIsGlobalNotScopedByUser() {
        repository.save(AiConfig.newConfig(USER_ID, defaultConfig("llm", "chat")));

        assertThat(resetDefaultSql())
                .as("默认配置是全局唯一的，加 userId 过滤会让每个用户各留一个默认")
                .doesNotContain("userId");
    }

    @Test
    void resetDefaultKeepsTypeAndStateScope() {
        repository.save(AiConfig.newConfig(USER_ID, defaultConfig("llm", "chat")));

        assertThat(resetDefaultSql()).contains("configType", "state", "isDefault");
    }

    @Test
    void resetDefaultNarrowsByModelTypeOnlyForLlm() {
        repository.save(AiConfig.newConfig(USER_ID, defaultConfig("llm", "chat")));

        assertThat(resetDefaultSql())
                .as("llm 的 chat/vision/intent/embedding 各保留一个默认")
                .contains("modelType");
    }

    @Test
    void resetDefaultIgnoresModelTypeForNonLlmTypes() {
        repository.save(AiConfig.newConfig(USER_ID, defaultConfig("oss", "脏值")));

        assertThat(resetDefaultSql()).doesNotContain("modelType");
    }

    private String resetDefaultSql() {
        // resetDefault 先 select 出待降级的旧默认（过滤条件都在这条查询上），再按 id 批量 update
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ConfigDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(configMapper).selectList(captor.capture());
        LambdaQueryWrapper<ConfigDO> wrapper = captor.getValue();
        // MyBatis-Plus 的条件片段惰性求值，先取一次 SQL 才有内容
        return wrapper.getTargetSql();
    }

    private static ConfigBO defaultConfig(String configType, String modelType) {
        ConfigBO bo = new ConfigBO();
        bo.setConfigType(configType);
        bo.setModelType(modelType);
        bo.setProvider("openai");
        bo.setConfigName("默认配置");
        bo.setIsDefault("1");
        return bo;
    }
}
