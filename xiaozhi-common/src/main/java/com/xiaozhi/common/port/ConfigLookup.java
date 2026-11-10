package com.xiaozhi.common.port;

import com.xiaozhi.common.model.bo.ConfigBO;

/**
 * AI/runtime 场景使用的最小配置查询端口，避免直接依赖完整配置服务。
 */
public interface ConfigLookup {

    ConfigBO getConfig(Integer configId);

    ConfigBO getDefaultConfig(String configType);

    ConfigBO getDefaultConfig(String configType, String modelType);
}
