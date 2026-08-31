package com.xiaozhi.config.service;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.port.ConfigLookup;
import com.xiaozhi.common.model.resp.ConfigResp;
import com.xiaozhi.common.model.resp.PageResp;

import java.util.List;

public interface ConfigService extends ConfigLookup {

    String CACHE_NAME = "XiaoZhi:SysConfig";

    PageResp<ConfigResp> page(int pageNo, int pageSize, String configType, String configName,
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

    @Override
    default List<ConfigBO> listConfigs(Integer userId, String configType, String provider, String modelType, String isDefault, String state) {
        return listBO(userId, configType, provider, modelType, isDefault, state);
    }
}
