package com.xiaozhi.config.infrastructure.convert;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.config.dal.mysql.dataobject.ConfigDO;
import com.xiaozhi.config.domain.AiConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * ConfigDO / ConfigBO ↔ AiConfig 聚合根转换器。
 */
@Component
public class ConfigConverter {

    /** DO → 聚合根（重建） */
    public AiConfig toDomain(ConfigDO d) {
        if (d == null) return null;
        return AiConfig.reconstitute(
                d.getConfigId(), d.getUserId(),
                d.getConfigType(), d.getProvider(),
                d.getConfigName(), d.getConfigDesc(), d.getModelType(),
                d.getAppId(), d.getApiKey(), d.getApiSecret(),
                d.getAk(), d.getSk(), d.getApiUrl(),
                d.getEnableThinking(),
                d.getState(), "1".equals(d.getIsDefault()),
                d.getCreateTime(), d.getUpdateTime());
    }

    /** 聚合根 → DO（持久化） */
    public ConfigDO toDO(AiConfig c) {
        ConfigDO d = new ConfigDO();
        d.setConfigId(c.getConfigId());
        d.setUserId(c.getUserId());
        d.setConfigName(c.getConfigName());
        d.setConfigDesc(c.getConfigDesc());
        d.setConfigType(c.getConfigType());
        d.setModelType(c.getModelType());
        d.setProvider(c.getProvider());
        d.setAppId(c.getAppId());
        d.setApiKey(c.getApiKey());
        d.setApiSecret(c.getApiSecret());
        d.setAk(c.getAk());
        d.setSk(c.getSk());
        d.setApiUrl(c.getApiUrl());
        d.setState(StringUtils.hasText(c.getState()) ? c.getState() : AiConfig.STATE_ENABLED);
        d.setIsDefault(c.isDefault() ? "1" : "0");
        d.setEnableThinking(c.getEnableThinking());
        return d;
    }

    /** BO → 聚合根（供 AppService 在 update 时构建更新参数） */
    public AiConfig toDomain(ConfigBO bo) {
        if (bo == null) return null;
        return AiConfig.reconstitute(
                bo.getConfigId(), bo.getUserId(),
                bo.getConfigType(), bo.getProvider(),
                bo.getConfigName(), bo.getConfigDesc(), bo.getModelType(),
                bo.getAppId(), bo.getApiKey(), bo.getApiSecret(),
                bo.getAk(), bo.getSk(), bo.getApiUrl(),
                bo.getEnableThinking(),
                bo.getState(), "1".equals(bo.getIsDefault()),
                null, null);
    }

    /** 聚合根 → BO（供查询路径复用） */
    public ConfigBO toBO(AiConfig c) {
        if (c == null) return null;
        ConfigBO bo = new ConfigBO();
        bo.setConfigId(c.getConfigId());
        bo.setUserId(c.getUserId());
        bo.setConfigName(c.getConfigName());
        bo.setConfigDesc(c.getConfigDesc());
        bo.setConfigType(c.getConfigType());
        bo.setModelType(c.getModelType());
        bo.setProvider(c.getProvider());
        bo.setAppId(c.getAppId());
        bo.setApiKey(c.getApiKey());
        bo.setApiSecret(c.getApiSecret());
        bo.setAk(c.getAk());
        bo.setSk(c.getSk());
        bo.setApiUrl(c.getApiUrl());
        bo.setState(c.getState());
        bo.setIsDefault(c.isDefault() ? "1" : "0");
        bo.setEnableThinking(c.getEnableThinking());
        bo.setCreateTime(c.getCreateTime());
        bo.setUpdateTime(c.getUpdateTime());
        return bo;
    }
}
