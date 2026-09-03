package com.xiaozhi.common.port;

import com.xiaozhi.common.model.bo.ConfigBO;

/**
 * 提供第三方平台 token 解析能力的窄接口。
 */
public interface TokenResolver {

    String getToken(ConfigBO config);

    /** 丢弃该配置已缓存的 token，下次获取时重新换取。 */
    void removeCache(ConfigBO config);
}
