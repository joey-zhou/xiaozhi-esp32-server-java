package com.xiaozhi.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.CacheHelper;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.resp.ConfigResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.config.convert.ConfigConvert;
import com.xiaozhi.config.dal.mysql.dataobject.ConfigDO;
import com.xiaozhi.config.dal.mysql.mapper.ConfigMapper;
import com.xiaozhi.config.service.ConfigService;
import jakarta.annotation.Resource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ConfigServiceImpl implements ConfigService {

    private static final List<String> EXCLUDED_PROVIDERS = List.of("coze", "dify", "xingchen");

    @Resource
    private ConfigMapper configMapper;

    @Resource
    private ConfigConvert configConvert;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private CacheHelper cacheHelper;

    @Override
    public PageResp<ConfigResp> page(int pageNo, int pageSize, String configType, String configName,
                                     String modelType, String provider, String isDefault, String state,
                                     Integer userId) {
        Page<ConfigDO> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<ConfigDO> query = buildQuery(userId, configType, provider, modelType, isDefault, state);
        if (StringUtils.hasText(configName)) {
            query.like(ConfigDO::getConfigName, configName);
        }
        IPage<ConfigDO> result = configMapper.selectPage(page, query);
        List<ConfigResp> list = result.getRecords().stream()
            .map(configConvert::toResp)
            .toList();
        return new PageResp<>(
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
        Cache cache = cacheManager.getCache(CACHE_NAME);
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

        String cacheKey = StringUtils.hasText(modelType)
            ? "default:" + configType + ":" + modelType
            : "default:" + configType;
        Cache cache = cacheManager.getCache(CACHE_NAME);
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
                if (result != null && cache != null) {
                    cache.put(cacheKey, result);
                }
                return result;
            }
        );
    }

    @Override
    public List<ConfigBO> listBO(Integer userId, String configType, String provider, String modelType, String isDefault, String state) {
        return configMapper.selectList(buildQuery(userId, configType, provider, modelType, isDefault, state)).stream()
            .map(configConvert::toBO)
            .toList();
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
