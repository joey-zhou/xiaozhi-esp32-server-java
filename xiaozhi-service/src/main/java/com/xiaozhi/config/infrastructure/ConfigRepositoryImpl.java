package com.xiaozhi.config.infrastructure;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaozhi.config.dal.mysql.dataobject.ConfigDO;
import com.xiaozhi.config.dal.mysql.mapper.ConfigMapper;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import com.xiaozhi.config.infrastructure.convert.ConfigConverter;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.event.AiConfigChangedEvent;
import jakarta.annotation.Resource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * AiConfig 聚合根仓储实现。
 * <p>
 * 维护"唯一默认"不变式：save 时若检测到 DEFAULT_CHANGED 信号，先批量清除同类其他默认，再保存。
 */
@Repository
public class ConfigRepositoryImpl implements ConfigRepository {

    @Resource
    private ConfigMapper configMapper;

    @Resource
    private ConfigConverter configConverter;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public Optional<AiConfig> findById(Integer configId) {
        if (configId == null) return Optional.empty();
        ConfigDO d = configMapper.selectById(configId);
        return Optional.ofNullable(configConverter.toDomain(d));
    }

    @Override
    @Transactional
    public void save(AiConfig config) {
        ConfigDO d = configConverter.toDO(config);

        var signals = config.pullSignals();
        if (signals.contains(AiConfig.DomainSignal.DEFAULT_CHANGED)) {
            resetDefault(config.getConfigType(), config.getModelType(), config.getConfigId());
        }

        if (config.getConfigId() == null) {
            configMapper.insert(d);
            config.assignId(d.getConfigId());
        } else {
            configMapper.updateById(d);
        }

        evictCache(config);

        if (signals.contains(AiConfig.DomainSignal.UPDATED) || signals.contains(AiConfig.DomainSignal.DISABLED)) {
            eventPublisher.publishEvent(new AiConfigChangedEvent(this, config.getConfigType(), config.getConfigId()));
        }
    }

    @Override
    @Transactional
    public void delete(Integer configId) {
        findById(configId).ifPresent(config -> {
            config.disable();
            ConfigDO d = configConverter.toDO(config);
            configMapper.updateById(d);
            evictCache(config);
            eventPublisher.publishEvent(new AiConfigChangedEvent(this, config.getConfigType(), configId));
        });
    }

    // ── 私有辅助 ──────────────────────────────────────────────────────────────

    /**
     * 清除同类型的其他默认配置。
     * <p>
     * oss/llm/tts 等均为管理员建立的全局配置（读取端 {@code getDefaultBO} 亦全局取默认），
     * 因此默认约束是全局唯一，不按 userId 过滤——否则跨用户会残留多个默认（如种子的 admin local
     * 存储与其他用户新建的默认并存）。
     * <p>
     * modelType 仅对 llm 有业务含义（chat/vision/intent/embedding 各保留一个默认）；oss/stt/tts
     * 为单默认，即使库中存在 modelType 脏值也不应据此细分，否则会因 modelType 不匹配而漏清旧默认。
     */
    private void resetDefault(String configType, String modelType, Integer excludeId) {
        LambdaUpdateWrapper<ConfigDO> w = new LambdaUpdateWrapper<ConfigDO>()
                .eq(ConfigDO::getConfigType, configType)
                .eq(ConfigDO::getState, AiConfig.STATE_ENABLED)
                .eq(ConfigDO::getIsDefault, "1")
                .set(ConfigDO::getIsDefault, "0");
        if ("llm".equals(configType) && StringUtils.hasText(modelType)) {
            w.eq(ConfigDO::getModelType, modelType);
        }
        if (excludeId != null) {
            w.ne(ConfigDO::getConfigId, excludeId);
        }
        configMapper.update(null, w);
    }

    private void evictCache(AiConfig config) {
        Cache cache = cacheManager.getCache(ConfigService.CACHE_NAME);
        if (cache == null) return;
        if (config.getConfigId() != null) {
            cache.evict(String.valueOf(config.getConfigId()));
        }
        if (StringUtils.hasText(config.getConfigType())) {
            cache.evict("default:" + config.getConfigType());
            if (StringUtils.hasText(config.getModelType())) {
                cache.evict("default:" + config.getConfigType() + ":" + config.getModelType());
            }
        }
    }
}
