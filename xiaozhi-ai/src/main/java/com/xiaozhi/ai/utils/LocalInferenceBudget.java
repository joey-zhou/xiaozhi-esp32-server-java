package com.xiaozhi.ai.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/**
 * 本地推理（识别、合成）共用的 CPU 核预算：只在正在使用的几方之间按权重分，
 * 没人用的那份不留着。角色换了服务商，新的一方首次推理时进来重分，
 * 旧的一方闲置超过 {@link #IDLE_WINDOW_NANOS} 后退出、份额还给其余各方。
 *
 * <p>分到的是核数而不是并发数，各方自己除以单次推理的线程数得到并发。
 */
@Slf4j
public final class LocalInferenceBudget {

    /** 预算的使用方；权重即各方同时在用时的分配比例 */
    public enum Kind {
        STT(2), TTS(1);

        private final int weight;

        Kind(int weight) {
            this.weight = weight;
        }
    }

    private static final long IDLE_WINDOW_NANOS = TimeUnit.MINUTES.toNanos(10);

    private static final LocalInferenceBudget SHARED = new LocalInferenceBudget(
            Runtime.getRuntime().availableProcessors(), IDLE_WINDOW_NANOS, System::nanoTime);

    private final int totalCores;
    private final long idleWindowNanos;
    private final LongSupplier nanoClock;
    /** 各方最近一次推理的时刻，0 表示从未用过 */
    private final AtomicLongArray lastUsed = new AtomicLongArray(Kind.values().length);
    // 并发容器：bind 不拿本对象的锁，调用方持着自己的锁来登记也不会与回调方向的加锁顺序相扣
    private final Map<Kind, IntConsumer> listeners = new ConcurrentHashMap<>();
    private final Map<Kind, Integer> applied = new ConcurrentHashMap<>();
    /** 上次分配时在用的各方，按 ordinal 置位 */
    private volatile int appliedMask;

    LocalInferenceBudget(int totalCores, long idleWindowNanos, LongSupplier nanoClock) {
        this.totalCores = Math.max(1, totalCores);
        this.idleWindowNanos = idleWindowNanos;
        this.nanoClock = nanoClock;
    }

    public static LocalInferenceBudget shared() {
        return SHARED;
    }

    /**
     * 登记份额变化时的回调，入参是分到的核数。并发由运维显式配死的一方不要登记，
     * 但仍要 {@link #touch}，它占着的核得算进别人的分母里。
     */
    public void bind(Kind kind, IntConsumer onCoresChanged) {
        listeners.put(kind, onCoresChanged);
        applied.remove(kind);
    }

    /** 每次推理前调用。在用的各方有进有出时当场重分，否则只记一个时间戳 */
    public void touch(Kind kind) {
        long now = nanoClock.getAsLong();
        // 0 留给「从未用过」
        lastUsed.set(kind.ordinal(), now == 0 ? 1 : now);
        int mask = activeMask(now);
        if (mask != appliedMask) {
            rebalance(mask);
        }
    }

    /** 该方此刻能分到的核数：按在用的各方再加上它自己来算 */
    public int coresFor(Kind kind) {
        return share(kind, activeMask(nanoClock.getAsLong()) | bit(kind));
    }

    private synchronized void rebalance(int mask) {
        if (mask == appliedMask) {
            return;
        }
        appliedMask = mask;
        for (Map.Entry<Kind, IntConsumer> entry : listeners.entrySet()) {
            Kind kind = entry.getKey();
            if ((mask & bit(kind)) == 0) {
                continue;
            }
            int cores = share(kind, mask);
            Integer previous = applied.put(kind, cores);
            if (previous == null || previous != cores) {
                entry.getValue().accept(cores);
            }
        }
        if (mask != 0) {
            log.info("本地推理核预算重分 - {}（共 {} 核）", describe(mask), totalCores);
        }
    }

    /**
     * 按权重分，向下取整、至少 1 核。单独一方最多拿四分之三，
     * 余下的留给 VAD、编解码与对话主链路。
     */
    private int share(Kind kind, int mask) {
        int weightSum = 0;
        for (Kind each : Kind.values()) {
            if ((mask & bit(each)) != 0) {
                weightSum += each.weight;
            }
        }
        int cores = totalCores * kind.weight / weightSum;
        return Math.max(1, Math.min(cores, totalCores * 3 / 4));
    }

    private int activeMask(long now) {
        int mask = 0;
        for (Kind each : Kind.values()) {
            long used = lastUsed.get(each.ordinal());
            if (used != 0 && now - used <= idleWindowNanos) {
                mask |= bit(each);
            }
        }
        return mask;
    }

    private static int bit(Kind kind) {
        return 1 << kind.ordinal();
    }

    private String describe(int mask) {
        StringBuilder shares = new StringBuilder();
        for (Kind each : Kind.values()) {
            if ((mask & bit(each)) != 0) {
                shares.append(shares.isEmpty() ? "" : ", ").append(each).append('=').append(share(each, mask));
            }
        }
        return shares.toString();
    }
}
