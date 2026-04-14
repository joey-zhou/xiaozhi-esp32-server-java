package com.xiaozhi.token;

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
        return expireAt <= System.currentTimeMillis();
    }

    public boolean shouldRefresh(long refreshAheadMillis) {
        return expireAt - System.currentTimeMillis() <= refreshAheadMillis;
    }
}
