package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dao.ConfigMapper;
import com.xiaozhi.dialogue.stt.factory.SttServiceFactory;
import com.xiaozhi.dialogue.token.factory.TokenServiceFactory;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.service.SysConfigService;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型配置
 * 
 * @author Joey
 * 
 */

@Service
public class SysConfigServiceImpl extends BaseServiceImpl implements SysConfigService {
    private final static String CACHE_NAME = "XiaoZhi:SysConfig";

    @Resource
    private ConfigMapper configMapper;

    @Resource
    private TokenServiceFactory tokenService;

    @Resource
    private SttServiceFactory sttServiceFactory;

    @Resource
    private TtsServiceFactory ttsServiceFactory;

    /**
     * 添加配置
     * 
     * @param config
     * @return
     */
    @Override
    @Transactional(transactionManager = "transactionManager")
    public int add(SysConfig config) {
        // 如果当前配置被设置为默认，则将同类型同用户的其他配置设置为非默认
        if (config.getIsDefault() != null && config.getIsDefault().equals("1")) {
            resetDefaultConfig(config);
        }
        return configMapper.add(config);
    }

    /**
     * 修改配置
     * 
     * @param config
     * @return
     */
    @Override
    @Transactional(transactionManager = "transactionManager")
    @Caching(evict = {
        @CacheEvict(value = CACHE_NAME, key = "#config.configId"),
        @CacheEvict(value = CACHE_NAME, key = "#config.modelType", condition = "#config.modelType != null")
    })
    public int update(SysConfig config) {
        // 如果当前配置被设置为默认，则将同类型同用户的其他配置设置为非默认
        if (config.getIsDefault() != null && config.getIsDefault().equals("1")) {
            resetDefaultConfig(config);
        }
        int rows = configMapper.update(config);
        if (rows > 0) {
            sttServiceFactory.removeCache(config);
            ttsServiceFactory.removeCache(config);
            List<SysConfig> configs = configMapper.query(config);
            // 这里可能为 null，
            if (configs.size() > 0) {
                tokenService.removeCache(configs.getFirst());
            }
        }
        return rows;
    }

    /**
     * 重置同类型同用户的默认配置
     * 
     * @param config
     */
    private void resetDefaultConfig(SysConfig config) {
        // 创建一个用于重置的配置对象
        SysConfig resetConfig = new SysConfig();
        resetConfig.setUserId(config.getUserId());
        // 其他类型正常处理，只重置同类型的配置
        resetConfig.setConfigType(config.getConfigType());
        resetConfig.setModelType(config.getModelType());
        configMapper.resetDefault(resetConfig);
    }

    /**
     * 查询模型
     * 
     * @param config
     * @return
     */
    @Override
    public List<SysConfig> query(SysConfig config, PageFilter pageFilter) {
        if(pageFilter != null){
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        return configMapper.query(config);
    }

    /**
     * 查询配置
     * 
     * @param configId 配置id
     * @return 具体的配置
     */
    @Override
    @Cacheable(value = CACHE_NAME, key = "#configId", unless = "#result == null")
    public SysConfig selectConfigById(Integer configId) {
        return configMapper.selectConfigById(configId);
    }

    /**
     * 查询默认配置
     *
     * @param modelType
     * @return 配置
     */
    @Override
    @Cacheable(value = CACHE_NAME, key ="#modelType", unless = "#result == null")
    public SysConfig selectModelType(String modelType) {
        SysConfig queryConfig = new SysConfig();
        queryConfig.setModelType(modelType);

        List<SysConfig> modelConfigs = configMapper.query(queryConfig);

        for (SysConfig config : modelConfigs) {
            if ("1".equals(config.getIsDefault())) {
                return config;  // 找到默认配置就返回
            }
        }

        // 没有默认配置，返回第一个即可
        return modelConfigs.isEmpty() ? null : modelConfigs.getFirst();
    }

}