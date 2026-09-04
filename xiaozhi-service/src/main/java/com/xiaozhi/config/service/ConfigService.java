package com.xiaozhi.config.service;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.port.ConfigLookup;
import com.xiaozhi.common.model.PageResult;
import org.springframework.util.Assert;

import java.util.List;

public interface ConfigService extends ConfigLookup {

    String CACHE_NAME = "XiaoZhi:SysConfig";

    PageResult<ConfigBO> page(int pageNo, int pageSize, String configType, String configName,
                              String modelType, String provider, String isDefault, String state,
                              Integer userId);

    ConfigBO getBO(Integer configId);

    ConfigBO getDefaultBO(String configType);

    ConfigBO getDefaultBO(String configType, String modelType);

    /**
     * 清除指定类型的「默认配置」缓存。
     * <p>
     * 跨实例场景下（如 dialogue 独立进程）收到配置变更广播后调用，确保下次 {@link #getDefaultBO}
     * 从数据库重读，而非命中其它实例回填的旧值。
     */
    void evictDefaultCache(String configType);

    List<ConfigBO> listBO(Integer userId, String configType, String provider, String modelType, String isDefault, String state);

    @Override
    default ConfigBO getConfig(Integer configId) {
        return getBO(configId);
    }

    @Override
    default ConfigBO getDefaultConfig(String configType) {
        return getDefaultBO(configType);
    }

    @Override
    default ConfigBO getDefaultConfig(String configType, String modelType) {
        return getDefaultBO(configType, modelType);
    }

    /**
     * 硬约束：AI 运行时的配置查询必须限定用户。userId 为空时 {@link #listBO} 会略过用户条件
     * 退化成全库查询，取到其他用户的凭据。
     */
    @Override
    default List<ConfigBO> listConfigs(Integer userId, String configType, String provider, String modelType, String isDefault, String state) {
        Assert.notNull(userId, "查询配置必须指定用户");
        return listBO(userId, configType, provider, modelType, isDefault, state);
    }
}
