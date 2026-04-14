package com.xiaozhi.token.provider;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.token.TokenCache;

import java.util.Collection;

/**
 * 第三方 token 获取策略。
 */
public interface TokenProvider {

    Collection<String> getSupportedProviders();

    TokenCache fetchToken(ConfigBO config);
}
