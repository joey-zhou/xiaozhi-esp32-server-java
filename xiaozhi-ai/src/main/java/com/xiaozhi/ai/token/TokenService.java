package com.xiaozhi.ai.token;

import com.xiaozhi.common.DistributedLocks;
import com.xiaozhi.event.AiConfigChangedEvent;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.port.ProviderTokenClient;
import com.xiaozhi.ai.token.provider.TokenProvider;
import com.xiaozhi.utils.DateUtils;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TokenService implements ProviderTokenClient {

    private static final String TOKEN_KEY_PREFIX = "xiaozhi:token:";
    private static final String LOCK_PREFIX = "token:";
    /** 别的实例正在换 token 时最多等这么久 */
    private static final Duration LOCK_WAIT = Duration.ofSeconds(4);
    private static final long REFRESH_AHEAD_MILLIS = Duration.ofHours(1).toMillis();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DistributedLocks distributedLocks;

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
        if (cached != null && !cached.shouldRefresh(REFRESH_AHEAD_MILLIS)) {
            return cached.getToken();
        }
        // 手里的还没过期就不排队等别人刷新
        Duration wait = cached != null ? Duration.ZERO : LOCK_WAIT;
        return distributedLocks.callWithin(buildLockName(config), wait, () -> refresh(config), () -> {
            if (cached != null) {
                return cached.getToken();
            }
            throw new IllegalStateException("获取Token失败，provider=" + config.getProvider() + ", configId=" + config.getConfigId());
        });
    }

    private String refresh(ConfigBO config) {
        TokenCache latest = getCachedToken(config);
        if (latest != null && !latest.shouldRefresh(REFRESH_AHEAD_MILLIS)) {
            return latest.getToken();
        }
        TokenCache refreshed = resolveProvider(config.getProvider()).fetchToken(config);
        validateToken(refreshed, config);
        cacheToken(config, refreshed);
        return refreshed.getToken();
    }

    /** token 按 provider:configId 存，配置可能刚把 provider 改掉，每个 provider 下的这份都删 */
    @Override
    public void removeCache(Integer configId) {
        if (configId == null) {
            return;
        }
        stringRedisTemplate.delete(providerMap.keySet().stream()
                .map(provider -> TOKEN_KEY_PREFIX + provider + ":" + configId)
                .toList());
    }

    /** 本进程内的配置变更，任何类型的配置都可能换过 token；对话进程那侧由 RedisSubscriber 转调 {@link #removeCache} */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAiConfigChanged(AiConfigChangedEvent event) {
        removeCache(event.getConfigId());
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
        long ttlMillis = Math.max(tokenCache.getExpireAt() - DateUtils.millis(), 1000L);
        stringRedisTemplate.opsForValue().set(buildTokenKey(config), json, Duration.ofMillis(ttlMillis));
    }

    private String buildTokenKey(ConfigBO config) {
        return TOKEN_KEY_PREFIX + normalizeProvider(config.getProvider()) + ":" + normalizeConfigId(config.getConfigId());
    }

    private String buildLockName(ConfigBO config) {
        return LOCK_PREFIX + normalizeProvider(config.getProvider()) + ":" + normalizeConfigId(config.getConfigId());
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
        if (tokenCache == null || !StringUtils.hasText(tokenCache.getToken()) || tokenCache.getExpireAt() <= DateUtils.millis()) {
            throw new IllegalStateException("Token无效，provider=" + config.getProvider() + ", configId=" + config.getConfigId());
        }
    }

    private String normalizeProvider(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase();
    }

    private String normalizeConfigId(Integer configId) {
        return configId == null ? "default" : String.valueOf(configId);
    }
}
