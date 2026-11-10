package com.xiaozhi.ai.tool;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.utils.DateUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 按对话轮次记下工具的执行进度。
 * <p>
 * 模型把工具调用交出去之后，在拿到工具结果之前一个字都不会再出，流上看就是一段沉默；
 * 首字超时与静默超时要把这段时间让出去，否则一个跑得久的工具会被当成模型卡死。
 * 工具自己有执行上限，让出去的时间是有界的。
 */
@Component
public class ToolCallActivityRegistry {

    /** 轮次结束后不再有人查，按访问时间过期回收 */
    private static final Duration RETENTION = Duration.ofMinutes(30);

    private final Cache<String, Activity> activities = Caffeine.newBuilder()
            .expireAfterAccess(RETENTION)
            .build();

    /** 轮次号是毫秒时间戳，不同设备可能撞号，要带上对话 owner */
    public static String key(String ownerId, long turnId) {
        return ownerId + "#" + turnId;
    }

    /** 从工具上下文取轮次键，缺 owner 或轮次号时返回 null */
    public static String keyOf(Map<String, Object> toolContext) {
        if (toolContext.get(ToolSession.CONTEXT_OWNER_ID) instanceof String ownerId
                && toolContext.get(ToolSession.CONTEXT_TURN_ID) instanceof Long turnId) {
            return key(ownerId, turnId);
        }
        return null;
    }

    public void begin(String key) {
        activities.get(key, k -> new Activity()).begin();
    }

    public void end(String key) {
        Activity activity = activities.getIfPresent(key);
        if (activity != null) {
            activity.end();
        }
    }

    /**
     * 模型还要再沉默多久才算满 limit：有工具在执行时按整段 limit 顺延；
     * 工具刚结束则从结束那一刻起算；本轮没执行过工具返回零
     */
    public Duration remainingQuiet(String key, Duration limit) {
        Activity activity = key == null ? null : activities.getIfPresent(key);
        return activity == null ? Duration.ZERO : activity.remainingQuiet(limit);
    }

    private static final class Activity {
        private int running;
        private long lastChangeNanos = System.nanoTime();

        synchronized void begin() {
            running++;
            lastChangeNanos = System.nanoTime();
        }

        synchronized void end() {
            running = Math.max(0, running - 1);
            lastChangeNanos = System.nanoTime();
        }

        synchronized Duration remainingQuiet(Duration limit) {
            if (running > 0) {
                return limit;
            }
            long left = limit.toMillis() - DateUtils.elapsedMillis(lastChangeNanos);
            return left > 0 ? Duration.ofMillis(left) : Duration.ZERO;
        }
    }
}
