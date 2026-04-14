package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;

/**
 * AI 模型配置变更事件（更新或删除）。
 * 由 ConfigRepositoryImpl.save() / delete() 发布，触发 STT/TTS/Token 缓存失效广播。
 */
public class AiConfigChangedEvent extends AbstractDomainEvent {

    private final String configType;
    private final Integer configId;

    public AiConfigChangedEvent(Object source, String configType, Integer configId) {
        super(source);
        this.configType = configType;
        this.configId = configId;
    }

    public String getConfigType() {
        return configType;
    }

    public Integer getConfigId() {
        return configId;
    }
}
