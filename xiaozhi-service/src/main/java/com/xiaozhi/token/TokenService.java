package com.xiaozhi.token;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.port.TokenResolver;
import com.xiaozhi.token.provider.TokenProvider;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TokenService implements TokenResolver {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    private static final String TOKEN_KEY_PREFIX = "xiaozhi:token:";
    private static final String LOCK_KEY_PREFIX = "xiaozhi:token:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(15);
    private static final long REFRESH_AHEAD_MILLIS = Duration.ofHours(1).toMillis();
    private static final int MAX_WAIT_RETRIES = 20;
    private static final long WAIT_INTERVAL_MILLIS = 200L;

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = createReleaseLockScript();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final List<TokenProvider> tokenProviders;

    private final Map<String, TokenProvider> providerMap = new HashMap<>();

    @Autowired
    public TokenService(List<TokenProvider> tokenProviders) {
        this.tokenProviders = tokenProviders;
    }

    @PostConstruct
    public void init() {
        for (TokenProvider tokenProvider : tokenProviders) {
            for (String provider : tokenProvider.getSupportedProviders()) {
                String normalized = normalizeProvider(provider);
                TokenProvider previous = providerMap.putIfAbsent(normalized, tokenProvider);
                if (previous != null) {
                    throw new IllegalStateException("重复的TokenProvider注册: " + normalized);
                }
            }
        }
    }

    @Override
    public String getToken(ConfigBO config) {
        validateConfig(config);
        TokenCache cached = getCachedToken(config);
        if (cached != null && !cached.isExpired() && !cached.shouldRefresh(REFRESH_AHEAD_MILLIS)) {
            return cached.getToken();
        }

        String lockKey = buildLockKey(config);
        String lockValue = UUID.randomUUID().toString();
        if (tryLock(lockKey, lockValue)) {
            try {
                TokenCache latest = getCachedToken(config);
                if (latest != null && !latest.isExpired() && !latest.shouldRefresh(REFRESH_AHEAD_MILLIS)) {
                    return latest.getToken();
                }
                TokenCache refreshed = resolveProvider(config.getProvider()).fetchToken(config);
                validateToken(refreshed, config);
                cacheToken(config, refreshed);
                return refreshed.getToken();
            } finally {
                unlock(lockKey, lockValue);
            }
        }

        if (cached != null && !cached.isExpired()) {
            return cached.getToken();
        }

        TokenCache waited = waitForToken(config);
        if (waited != null && !waited.isExpired()) {
            return waited.getToken();
        }
        throw new IllegalStateException("获取Token失败，provider=" + config.getProvider() + ", configId=" + config.getConfigId());
    }

    public void removeCache(ConfigBO config) {
        if (config == null || !StringUtils.hasText(config.getProvider())) {
            return;
        }
        stringRedisTemplate.delete(buildTokenKey(config));
    }

    private TokenProvider resolveProvider(String provider) {
        TokenProvider tokenProvider = providerMap.get(normalizeProvider(provider));
        if (tokenProvider == null) {
            throw new IllegalArgumentException("不支持的Token服务提供商: " + provider);
        }
        return tokenProvider;
    }

    private TokenCache getCachedToken(ConfigBO config) {
        String json = stringRedisTemplate.opsForValue().get(buildTokenKey(config));
        if (!StringUtils.hasText(json)) {
            return null;
        }
        TokenCache tokenCache = JsonUtil.fromJson(json, TokenCache.class);
        if (tokenCache == null || !StringUtils.hasText(tokenCache.getToken()) || tokenCache.getExpireAt() <= 0L) {
            stringRedisTemplate.delete(buildTokenKey(config));
            return null;
        }
        if (tokenCache.isExpired()) {
            stringRedisTemplate.delete(buildTokenKey(config));
            return null;
        }
        return tokenCache;
    }

    private void cacheToken(ConfigBO config, TokenCache tokenCache) {
        String json = JsonUtil.toJson(tokenCache);
        if (json == null) {
            throw new IllegalStateException("Token缓存序列化失败");
        }
        long ttlMillis = Math.max(tokenCache.getExpireAt() - System.currentTimeMillis(), 1000L);
        stringRedisTemplate.opsForValue().set(buildTokenKey(config), json, Duration.ofMillis(ttlMillis));
    }

    private boolean tryLock(String lockKey, String lockValue) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL);
        return Boolean.TRUE.equals(success);
    }

    private void unlock(String lockKey, String lockValue) {
        stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockValue);
    }

    private TokenCache waitForToken(ConfigBO config) {
        for (int i = 0; i < MAX_WAIT_RETRIES; i++) {
            try {
                Thread.sleep(WAIT_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            TokenCache tokenCache = getCachedToken(config);
            if (tokenCache != null) {
                return tokenCache;
            }
        }
        return null;
    }

    private String buildTokenKey(ConfigBO config) {
        return TOKEN_KEY_PREFIX + normalizeProvider(config.getProvider()) + ":" + normalizeConfigId(config.getConfigId());
    }

    private String buildLockKey(ConfigBO config) {
        return LOCK_KEY_PREFIX + normalizeProvider(config.getProvider()) + ":" + normalizeConfigId(config.getConfigId());
    }

    private void validateConfig(ConfigBO config) {
        if (config == null) {
            throw new IllegalArgumentException("Token配置不能为空");
        }
        if (!StringUtils.hasText(config.getProvider())) {
            throw new IllegalArgumentException("Token配置provider不能为空");
        }
        if (config.getConfigId() == null) {
            throw new IllegalArgumentException("Token配置configId不能为空");
        }
    }

    private void validateToken(TokenCache tokenCache, ConfigBO config) {
        if (tokenCache == null || !StringUtils.hasText(tokenCache.getToken()) || tokenCache.getExpireAt() <= System.currentTimeMillis()) {
            throw new IllegalStateException("Token无效，provider=" + config.getProvider() + ", configId=" + config.getConfigId());
        }
    }

    private String normalizeProvider(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase();
    }

    private String normalizeConfigId(Integer configId) {
        return configId == null ? "default" : String.valueOf(configId);
    }

    private static DefaultRedisScript<Long> createReleaseLockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
        return script;
    }
}
