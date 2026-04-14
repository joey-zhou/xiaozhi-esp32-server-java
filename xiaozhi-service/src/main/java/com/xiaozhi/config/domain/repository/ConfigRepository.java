package com.xiaozhi.config.domain.repository;

import com.xiaozhi.config.domain.AiConfig;

import java.util.Optional;

/**
 * AiConfig 聚合根仓储接口（领域层定义，基础设施层实现）。
 * <p>
 * save() 时自动维护"同 userId + configType + modelType 下唯一默认"不变式。
 */
public interface ConfigRepository {

    /** 按 configId 加载聚合根 */
    Optional<AiConfig> findById(Integer configId);

    /**
     * 持久化聚合根（新建或更新）。
     * <p>若聚合根携带 DEFAULT_CHANGED 信号，实现需先 resetDefault 再保存，然后清空缓存。
     */
    void save(AiConfig config);

    /**
     * 软删除配置（state=disabled, isDefault=0）并清除缓存。
     * <p>实际执行 {@link AiConfig#disable()} 后由此方法完成持久化。
     */
    void delete(Integer configId);
}
