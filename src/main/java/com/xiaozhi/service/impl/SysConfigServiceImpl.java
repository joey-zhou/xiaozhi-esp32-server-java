package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.dao.ConfigMapper;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.service.SysConfigService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

/**
 * 模型配置
 * 
 * @author Joey
 * 
 */

@Service
public class SysConfigServiceImpl implements SysConfigService {

    @Resource
    private ConfigMapper configMapper;

    /**
     * 添加配置
     * 
     * @param SysConfig
     * @return
     */
    @Override
    @Transactional
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
     * @param SysConfig
     * @return
     */
    @Override
    @Transactional
    public int update(SysConfig config) {
        // 如果当前配置被设置为默认，则将同类型同用户的其他配置设置为非默认
        if (config.getIsDefault() != null && config.getIsDefault().equals("1")) {
            resetDefaultConfig(config);
        }
        return configMapper.update(config);
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
        configMapper.resetDefault(resetConfig);
    }

    /**
     * 查询模型
     * 
     * @param config
     * @return
     */
    @Override
    @Transactional
    public List<SysConfig> query(SysConfig config) {
        if (config.getLimit() != null && config.getLimit() > 0) {
            PageHelper.startPage(config.getStart(), config.getLimit());
        }
        // 这里为了适配阿里云的 Token 刷新，返回的结果会有重复
        List<SysConfig> configs = configMapper.query(config);
        Map<Integer, SysConfig> uniqueConfigs = new LinkedHashMap<>();
        for (SysConfig c : configs) {
            uniqueConfigs.put(c.getConfigId(), c); // 利用 Map 的键唯一特性去重
        }
        return new ArrayList<>(uniqueConfigs.values());
    }

    /**
     * 查询配置
     * 
     * @param configId
     * @return
     */
    @Override
    public SysConfig selectConfigById(Integer configId) {
        return configMapper.selectConfigById(configId);
    }

}