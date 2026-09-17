package com.xiaozhi.config.service;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.port.ConfigLookup;
import com.xiaozhi.common.model.PageResult;
import org.springframework.util.Assert;

import java.util.List;

public interface ConfigService extends ConfigLookup {

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

    /**
     * 落库第三方智能体平台（coze/dify/xingchen）同步来的模型配置，configType 固定为 llm。
     * <p>
     * configId 为空表示平台上新发现的智能体，建一条；带 configId 表示平台侧内容有变化，按 id 更新。
     * 智能体那侧只需要这一个写入口，配置聚合的仓储不外借。
     *
     * @return 落库后的配置
     */
    ConfigBO saveAgentModel(ConfigBO agentModel);

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
     * AI 运行时的配置查询必须限定用户。userId 为空时 {@link #listBO} 会略过用户条件
     * 退化成全库查询，取到其他用户的凭据。
     */
    @Override
    default List<ConfigBO> listConfigs(Integer userId, String configType, String provider, String modelType, String isDefault, String state) {
        Assert.notNull(userId, "查询配置必须指定用户");
        return listBO(userId, configType, provider, modelType, isDefault, state);
    }
}
