package com.xiaozhi.common;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;

/**
 * 按键串行的异步任务队列，跑在虚拟线程上。
 * <p>
 * 用于把阻塞工作（JDBC 落库等）移出调用方线程，同时保证同一个键上的任务按提交顺序执行。
 * 对话落库依赖这个顺序：助手消息先 INSERT，音频路径与打断截断才 UPDATE 得到，乱序会让 UPDATE 落空。
 * 不同键之间互不排队。
 * <p>
 * 队列跑空后条目自动摘除，不需要显式的清理钩子。
 */
@Slf4j
public final class SerialTaskRegistry {

    private static final CompletableFuture<Void> COMPLETED = CompletableFuture.completedFuture(null);

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static final ConcurrentHashMap<String, CompletableFuture<Void>> CHAINS = new ConcurrentHashMap<>();

    private SerialTaskRegistry() {
    }

    /**
     * 把任务排到该键的队尾。调用方线程不等待执行结果。
     *
     * @param key 串行键，为空时不参与排队，直接异步执行
     */
    public static void submit(String key, Runnable task) {
        if (key == null || key.isEmpty()) {
            EXECUTOR.execute(() -> run(task));
            return;
        }
        CompletableFuture<Void> tail = CHAINS.compute(key, (ignored, previous) ->
                (previous == null ? COMPLETED : previous).handleAsync((r, e) -> {
                    run(task);
                    return null;
                }, EXECUTOR));
        // 自己仍是队尾时才摘除，与 compute 争用同一个桶锁，不会丢掉后来者；
        // 任务抛 Error 时链上的 future 异常完成，摘除也必须照做，否则条目永远留在表里
        tail.whenComplete((ignored, error) -> CHAINS.remove(key, tail));
    }

    private static void run(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("串行任务执行失败", e);
        }
    }
}
