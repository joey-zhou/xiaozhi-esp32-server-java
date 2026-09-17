package com.xiaozhi.communication.common;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

/**
 * 按 key 串行执行的任务队列：同一个 key 的任务严格按提交顺序执行且互不重叠，不同 key 之间并行。
 * 任务跑在虚拟线程上，允许在任务体内做数据库、远程调用等阻塞操作。
 *
 * <p>
 * <ul>
 *   <li>只保证「提交顺序 == 执行顺序」，同一个 key 的提交方必须自身有序（如同一条 Netty EventLoop）；</li>
 *   <li>每个 key 的待执行队列有界，积压超过上限时丢弃最旧的任务，实时音频链路不允许无限排队；</li>
 *   <li>任务体不得持有需要显式释放的资源（如 ByteBuf），被丢弃的任务不会执行。</li>
 * </ul>
 */
@Slf4j
public class SessionSerialExecutor {

    private final ExecutorService delegate;
    private final String name;
    private final int queueLimit;
    private final Map<String, Lane> lanes = new ConcurrentHashMap<>();
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean stopped;

    public SessionSerialExecutor(String name, int queueLimit) {
        this.name = name;
        this.queueLimit = queueLimit;
        this.delegate = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(name + "-", 0).factory());
    }

    /**
     * 提交一个任务，同一 key 的任务按提交顺序串行执行。
     */
    public void execute(String key, Runnable task) {
        if (stopped) {
            return;
        }
        while (true) {
            Lane lane = lanes.computeIfAbsent(key, Lane::new);
            if (lane.offer(task)) {
                return;
            }
        }
    }

    /** 被丢弃的任务总数 */
    public long droppedCount() {
        return dropped.get();
    }

    public void shutdown() {
        stopped = true;
        lanes.clear();
        delegate.shutdownNow();
    }

    private final class Lane {
        private final String key;
        private final Deque<Runnable> queue = new ArrayDeque<>();
        private boolean running;
        /** 队列已被摘出 lanes，后续提交必须换一条新队列，否则同 key 会出现两条并行队列 */
        private boolean retired;
        private boolean overflowing;

        private Lane(String key) {
            this.key = key;
        }

        private synchronized boolean offer(Runnable task) {
            if (retired) {
                return false;
            }
            while (queue.size() >= queueLimit) {
                queue.pollFirst();
                long total = dropped.incrementAndGet();
                if (!overflowing) {
                    overflowing = true;
                    log.warn("[{}] 会话任务积压超过{}，丢弃最旧任务 - Key: {}, 累计丢弃: {}",
                            name, queueLimit, key, total);
                }
            }
            queue.addLast(task);
            if (!running) {
                running = true;
                try {
                    delegate.execute(this::drain);
                } catch (RejectedExecutionException e) {
                    // 关闭过程中还有报文进来，丢弃即可
                    running = false;
                    queue.clear();
                }
            }
            return true;
        }

        private void drain() {
            while (true) {
                Runnable next;
                synchronized (this) {
                    next = queue.pollFirst();
                    if (next == null) {
                        running = false;
                        retired = true;
                        overflowing = false;
                        lanes.remove(key, this);
                        return;
                    }
                    if (queue.isEmpty()) {
                        overflowing = false;
                    }
                }
                try {
                    next.run();
                } catch (Exception e) {
                    log.error("[{}] 会话任务执行失败 - Key: {}", name, key, e);
                }
            }
        }
    }
}
