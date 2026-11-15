package com.xiaozhi.common.model.bo;

/** 跨实例的配置变更载荷：各实例按它清掉对应的派生缓存 */
public record ConfigChangeBO(String configType, Integer configId) {
}
