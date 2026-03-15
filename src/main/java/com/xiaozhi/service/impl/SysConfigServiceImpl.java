package com.xiaozhi.service.impl;

import com.xiaozhi.common.cache.CacheHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dialogue.stt.factory.SttServiceFactory;
import com.xiaozhi.dialogue.token.factory.TokenServiceFactory;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.dialogue.tts.providers.AliyunNlsTtsService;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.repository.SysConfigRepository;
import com.xiaozhi.service.SysConfigService;
import jakarta.annotation.Resource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private SysConfigRepository sysConfigRepository;

    @Resource
    private TokenServiceFactory tokenService;

    @Resource
    private SttServiceFactory sttServiceFactory;

    @Resource
    private TtsServiceFactory ttsServiceFactory;

    @Resource
    private CacheHelper cacheHelper;

    @Resource
    private CacheManager cacheManager;

    /**
     * 添加配置
     *
     * @param config
     * @return
     */
    @Override
    @Transactional
    public int add(SysConfig config) {
        // 如果当前配置被设置为默认，则将同类型同用户的其他配置设置为非默认
        if (config.getIsDefault() != null && config.getIsDefault().equals("1")) {
            resetDefaultConfig(config);
        }
        return sysConfigRepository.add(config);
    }

    /**
     * 修改配置
     *
     * @param config
     * @return
     */
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_NAME, key = "#config.configId"),
        @CacheEvict(value = CACHE_NAME, key = "#config.modelType", condition = "#config.modelType != null")
    })
    public int update(SysConfig config) {
        // 如果当前配置被设置为默认，则将同类型同用户的其他配置设置为非默认
        if (config.getIsDefault() != null && config.getIsDefault().equals("1")) {
            resetDefaultConfig(config);
        }
        int rows = sysConfigRepository.update(config);
        if (rows > 0) {
            sttServiceFactory.removeCache(config);
            ttsServiceFactory.removeCache(config);
            // 清除阿里云NLS的NlsClient连接缓存，确保下次使用新配置重建连接
            AliyunNlsTtsService.clearClientCache(config.getConfigId());
            List<SysConfig> configs = sysConfigRepository.query(config);
            // 这里可能为 null，
            if (!configs.isEmpty()) {
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
        sysConfigRepository.resetDefault(resetConfig);
    }

    /**
     * 查询模型
     *
     * @param config
     * @return
     */
    @Override
    public List<SysConfig> query(SysConfig config, PageFilter pageFilter) {
        PageRequest pageRequest = null;
        if (pageFilter != null) {
//            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
            pageRequest = PageRequest.of(pageFilter.getStart() - 1, pageFilter.getLimit(), Sort.by(Sort.Direction.DESC,
                    "createTime"));
        }
        List<SysConfig> list = sysConfigRepository.query(config, pageRequest);

        return list;
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
        return sysConfigRepository.selectConfigById(configId);
    }

    /**
     * 查询默认配置
     * 使用分布式锁防止缓存击穿
     *
     * @param modelType 模型类型
     * @return 配置
     */
    @Override
    public SysConfig selectModelType(String modelType) {
        // 使用分布式锁防止缓存击穿
        return cacheHelper.getWithLock(
            "config:modelType:" + modelType,
            // 从缓存获取
            () -> {
                Cache cache = cacheManager.getCache(CACHE_NAME);
                if (cache != null) {
                    Cache.ValueWrapper wrapper = cache.get(modelType);
                    if (wrapper != null) {
                        return (SysConfig) wrapper.get();
                    }
                }
                return null;
            },
            // 从数据库获取
            () -> {
                SysConfig queryConfig = new SysConfig();
                queryConfig.setModelType(modelType);

                List<SysConfig> modelConfigs = sysConfigRepository.query(queryConfig);

                SysConfig result = null;
                for (SysConfig config : modelConfigs) {
                    if ("1".equals(config.getIsDefault())) {
                        result = config;  // 找到默认配置
                        break;
                    }
                }

                // 没有默认配置，返回第一个
                if (result == null && !modelConfigs.isEmpty()) {
                    result = modelConfigs.getFirst();
                }

                // 手动写入缓存
                if (result != null) {
                    Cache cache = cacheManager.getCache(CACHE_NAME);
                    if (cache != null) {
                        cache.put(modelType, result);
                    }
                }

                return result;
            }
        );
    }

}