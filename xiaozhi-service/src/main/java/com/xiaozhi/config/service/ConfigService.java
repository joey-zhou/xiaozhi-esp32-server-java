package com.xiaozhi.config.service;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.port.ConfigLookup;
import com.xiaozhi.common.model.PageResult;

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

    /**
     * 智能体对话用的平台凭据。dify、xingchen 一份凭据落成一个智能体，按智能体配置上的 apiKey 找回那一份；
     * coze 一份凭据下挂多个智能体，见 {@link #getPlatformCredential}。没有可用的凭据时返回 null
     */
    ConfigBO getAgentCredential(ConfigBO agentModel);

    /** coze 这类一份凭据下挂多个智能体的平台，用户用的那份凭据；有多份时取最早建的，同步与对话取到的是同一份 */
    ConfigBO getPlatformCredential(Integer userId, String provider);

    /** dify、xingchen 的平台凭据改了 apiKey 或地址，它落成的智能体配置跟着改，绑定这些智能体的角色不用重选 */
    void followAgentCredential(ConfigBO before, ConfigBO after);

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
}
