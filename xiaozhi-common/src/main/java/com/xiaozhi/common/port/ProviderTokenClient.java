package com.xiaozhi.common.port;

import com.xiaozhi.common.model.bo.ConfigBO;

/**
 * 向第三方平台换取 token 的窄接口，ai 模块实现、service 消费。
 */
public interface ProviderTokenClient {

    String getToken(ConfigBO config);

    /** 丢弃这份配置已缓存的 token，下次获取时重新换取。只认 configId：配置可能刚把 provider 改掉 */
    void removeCache(Integer configId);
}
