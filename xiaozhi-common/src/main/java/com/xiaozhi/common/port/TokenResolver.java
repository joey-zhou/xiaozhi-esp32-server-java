package com.xiaozhi.common.port;

import com.xiaozhi.common.model.bo.ConfigBO;

/**
 * 提供第三方平台 token 解析能力的窄接口。
 */
public interface TokenResolver {

    String getToken(ConfigBO config);
}
