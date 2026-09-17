package com.xiaozhi.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.agent.AgentProviders;
import com.xiaozhi.common.CacheHelper;
import com.xiaozhi.common.config.CacheNames;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.config.convert.ConfigConvert;
import com.xiaozhi.config.dal.mysql.dataobject.ConfigDO;
import com.xiaozhi.config.dal.mysql.mapper.ConfigMapper;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.config.support.ConfigCacheKeys;
import jakarta.annotation.Resource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ConfigServiceImpl implements ConfigService {

    private static final List<String> EXCLUDED_PROVIDERS = List.copyOf(AgentProviders.ALL);

    /** 第三方平台的智能体在本系统里只以 llm 配置的形态存在 */
    private static final String AGENT_MODEL_CONFIG_TYPE = "llm";

    @Resource
    private ConfigMapper configMapper;

    @Resource
    private ConfigConvert configConvert;

    @Resource
    private ConfigRepository configRepository;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private CacheHelper cacheHelper;

    @Override
    public PageResult<ConfigBO> page(int pageNo, int pageSize, String configType, String configName,
                                     String modelType, String provider, String isDefault, String state,
                                     Integer userId) {
        Page<ConfigDO> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<ConfigDO> query = buildQuery(userId, configType, provider, modelType, isDefault, state);
        if (StringUtils.hasText(configName)) {
            query.like(ConfigDO::getConfigName, configName);
        }
        IPage<ConfigDO> result = configMapper.selectPage(page, query);
        List<ConfigBO> list = result.getRecords().stream()
            .map(configConvert::toBO)
            .toList();
        return new PageResult<>(
            list,
            result.getTotal(),
            Math.toIntExact(result.getCurrent()),
            Math.toIntExact(result.getSize())
        );
    }

    @Override
    public ConfigBO getBO(Integer configId) {
        if (configId == null || configId <= 0) {
            return null;
        }
        String cacheKey = String.valueOf(configId);
        Cache cache = cacheManager.getCache(CacheNames.SYS_CONFIG);
        return cacheHelper.getWithLock(
            "config:" + cacheKey,
            () -> cache == null ? null : cache.get(cacheKey, ConfigBO.class),
            () -> {
                ConfigBO result = configConvert.toBO(configMapper.selectById(configId));
                if (result != null && cache != null) {
                    cache.put(cacheKey, result);
                }
                return result;
            }
        );
    }

    public ConfigBO getDefaultBO(String configType) {
        return getDefaultBO(configType, null);
    }

    @Override
    public ConfigBO getDefaultBO(String configType, String modelType) {
        if (!StringUtils.hasText(configType)) {
            return null;
        }

        String cacheKey = ConfigCacheKeys.defaultKey(configType, modelType);
        String absentKey = ConfigCacheKeys.absentKey(cacheKey);
        Cache cache = cacheManager.getCache(CacheNames.SYS_CONFIG);
        // 查过确认没有默认配置时直接返回；配置写入时 ConfigRepositoryImpl 会连同这个标记一起淘汰
        if (cache != null && cache.get(absentKey) != null) {
            return null;
        }
        return cacheHelper.getWithLock(
            "config:default:" + cacheKey,
            () -> cache == null ? null : cache.get(cacheKey, ConfigBO.class),
            () -> {
                ConfigDO configDO = configMapper.selectOne(buildQuery(
                    null,
                    configType,
                    null,
                    modelType,
                    null,
                    ConfigBO.STATE_ENABLED
                ).last("LIMIT 1"));
                ConfigBO result = configConvert.toBO(configDO);
                if (cache != null) {
                    if (result == null) {
                        cache.put(absentKey, Boolean.TRUE);
                    } else {
                        cache.put(cacheKey, result);
                    }
                }
                return result;
            }
        );
    }

    @Override
    public void evictDefaultCache(String configType) {
        if (!StringUtils.hasText(configType)) {
            return;
        }
        // 清除无 modelType 的默认缓存；llm 的各 modelType 变体由各自变更时清理
        String cacheKey = ConfigCacheKeys.defaultKey(configType, null);
        Cache cache = cacheManager.getCache(CacheNames.SYS_CONFIG);
        CacheHelper.evictNow(cache, cacheKey);
        CacheHelper.evictNow(cache, ConfigCacheKeys.absentKey(cacheKey));
    }

    @Override
    public List<ConfigBO> listBO(Integer userId, String configType, String provider, String modelType, String isDefault, String state) {
        return configMapper.selectList(buildQuery(userId, configType, provider, modelType, isDefault, state)).stream()
            .map(configConvert::toBO)
            .toList();
    }

    @Override
    @Transactional
    public ConfigBO saveAgentModel(ConfigBO agentModel) {
        Assert.notNull(agentModel, "同步智能体配置不能为空");
        Assert.notNull(agentModel.getUserId(), "同步智能体配置必须指定用户");
        // 智能体只会落成 llm 配置，类型不由调用方决定
        ConfigBO model = agentModel.setConfigType(AGENT_MODEL_CONFIG_TYPE);
        AiConfig config;
        if (model.getConfigId() == null) {
            config = AiConfig.newConfig(model.getUserId(), model);
        } else {
            config = configRepository.findById(model.getConfigId())
                .orElseThrow(() -> new ResourceNotFoundException("配置不存在: " + model.getConfigId()));
            config.update(model);
        }
        configRepository.save(config);
        return getBO(config.getConfigId());
    }

    private LambdaQueryWrapper<ConfigDO> buildQuery(Integer userId, String configType, String provider,
                                                    String modelType, String isDefault, String state) {
        LambdaQueryWrapper<ConfigDO> queryWrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            queryWrapper.eq(ConfigDO::getUserId, userId);
        }
        if (StringUtils.hasText(state)) {
            queryWrapper.eq(ConfigDO::getState, state);
        } else {
            queryWrapper.eq(ConfigDO::getState, ConfigBO.STATE_ENABLED);
        }
        if (StringUtils.hasText(configType)) {
            queryWrapper.eq(ConfigDO::getConfigType, configType);
        }
        if (StringUtils.hasText(modelType)) {
            queryWrapper.eq(ConfigDO::getModelType, modelType);
        }
        if (StringUtils.hasText(isDefault)) {
            queryWrapper.eq(ConfigDO::getIsDefault, isDefault);
        }
        if (StringUtils.hasText(provider)) {
            queryWrapper.eq(ConfigDO::getProvider, provider);
        } else {
            queryWrapper.notIn(ConfigDO::getProvider, EXCLUDED_PROVIDERS);
        }
        return queryWrapper.orderByDesc(ConfigDO::getIsDefault, ConfigDO::getCreateTime);
    }

}
