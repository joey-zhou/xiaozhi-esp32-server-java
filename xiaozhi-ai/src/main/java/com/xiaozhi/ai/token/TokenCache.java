package com.xiaozhi.ai.token;

import com.xiaozhi.utils.DateUtils;

/**
 * Redis 中缓存的 token 值。
 */
public class TokenCache {

    private String token;

    private long expireAt;

    public TokenCache() {
    }

    public TokenCache(String token, long expireAt) {
        this.token = token;
        this.expireAt = expireAt;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }

    public boolean isExpired() {
        return expireAt <= DateUtils.millis();
    }

    public boolean shouldRefresh(long refreshAheadMillis) {
        return expireAt - DateUtils.millis() <= refreshAheadMillis;
    }
}
