package com.xiaozhi.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaozhi.common.CacheHelper;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.config.convert.ConfigConvert;
import com.xiaozhi.config.dal.mysql.dataobject.ConfigDO;
import com.xiaozhi.config.dal.mysql.mapper.ConfigMapper;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(ConfigDO.class);
    }

    @Mock
    private ConfigMapper configMapper;

    @Mock
    private ConfigConvert configConvert;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private CacheHelper cacheHelper;

    @Mock
    private Cache cache;

    @InjectMocks
    private ConfigServiceImpl configService;

    @Test
    void listBOReturnsMappedConfigs() {
        ConfigDO configDO = new ConfigDO();
        configDO.setConfigId(1);
        ConfigBO configBO = new ConfigBO();
        configBO.setConfigId(1);

        when(configMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(configDO));
        when(configConvert.toBO(configDO)).thenReturn(configBO);

        List<ConfigBO> result = configService.listBO(1, "llm", "openai", "chat", null, ConfigBO.STATE_ENABLED);

        assertThat(result).containsExactly(configBO);
        verify(configMapper).selectList(any(LambdaQueryWrapper.class));
        verify(configConvert).toBO(configDO);
    }

    @Test
    void getBOReturnsNullForNonPositiveId() {
        assertThat(configService.getBO(-1)).isNull();
        verifyNoInteractions(configMapper, configConvert, cacheManager, cacheHelper);
    }

    @Test
    void getDefaultBOLoadsFromDbThroughCacheHelper() {
        ConfigDO configDO = new ConfigDO();
        configDO.setConfigId(10);
        ConfigBO configBO = new ConfigBO();
        configBO.setConfigId(10);

        when(cacheManager.getCache("XiaoZhi:SysConfig")).thenReturn(cache);
        when(cache.get("default:llm:chat", ConfigBO.class)).thenReturn(null);
        when(cacheHelper.getWithLock(anyString(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<ConfigBO> cacheSupplier = invocation.getArgument(1);
            @SuppressWarnings("unchecked")
            Supplier<ConfigBO> dbSupplier = invocation.getArgument(2);
            ConfigBO cached = cacheSupplier.get();
            return cached != null ? cached : dbSupplier.get();
        });
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configDO);
        when(configConvert.toBO(configDO)).thenReturn(configBO);

        ConfigBO result = configService.getDefaultBO("llm", "chat");

        assertThat(result).isSameAs(configBO);
        verify(cache).put("default:llm:chat", configBO);
    }

    @Test
    void getDefaultBOReturnsNullWhenConfigTypeBlank() {
        assertThat(configService.getDefaultBO(" ")).isNull();
        verifyNoInteractions(configMapper, configConvert, cacheManager);
    }
}
