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
