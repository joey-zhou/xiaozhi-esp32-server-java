package com.xiaozhi.communication.protocol;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 协议套件的异步等待工具。
 *
 * <p>协议链路上有 6 处 {@code Thread.startVirtualThread}（设备状态写库、MCP 初始化、STT 启动、
 * 唤醒词音频落盘等）以及 ScheduledPlayer 的发送线程，断言必须等条件成立而不是等固定时长。
 * 用例里禁止 {@code Thread.sleep} 加立即断言，一律走本类轮询。
 *
 * <p>反向断言（"某件事从未发生"）不能直接 verify never：要先用 {@link #until} 等一个
 * 可观测的后置信号让链路 settle，再验；否则是假绿。必要时用 {@link #stayFalse} 明确表达。
 */
final class AwaitHelper {

    /** 默认等待上限，够覆盖虚拟线程调度与 reactor boundedElastic 切换 */
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(2);

    private AwaitHelper() {
    }

    static void until(String description, BooleanSupplier condition) {
        until(description, DEFAULT_TIMEOUT, condition);
    }

    /** 轮询直到条件成立，超时抛 AssertionError 并带上条件描述 */
    static void until(String description, Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            LockSupport.parkNanos(POLL_INTERVAL.toNanos());
        }
        if (condition.getAsBoolean()) {
            return;
        }
        throw new AssertionError("等待超时（" + timeout.toMillis() + "ms）：" + description);
    }

    /** 轮询直到取值非 null 并返回它 */
    static <T> T untilPresent(String description, Supplier<T> supplier) {
        return untilPresent(description, DEFAULT_TIMEOUT, supplier);
    }

    static <T> T untilPresent(String description, Duration timeout, Supplier<T> supplier) {
        until(description, timeout, () -> supplier.get() != null);
        return supplier.get();
    }

    /**
     * 在给定时长内条件必须始终为假，用于"某件事不该发生"的断言。
     * 时长取短值即可，配合前置的 {@link #until} settle 使用。
     */
    static void stayFalse(String description, Duration duration, BooleanSupplier condition) {
        long deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                throw new AssertionError("不该发生却发生了：" + description);
            }
            LockSupport.parkNanos(POLL_INTERVAL.toNanos());
        }
    }
}
