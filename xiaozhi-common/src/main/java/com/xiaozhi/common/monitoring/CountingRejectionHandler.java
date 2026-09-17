package com.xiaozhi.common.monitoring;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 包一层计数的 RejectedExecutionHandler：任务被拒绝时先计数，再把处理逻辑原样交给目标策略，
 * 抛出的异常与目标策略完全一致，只是多了一个可观测的拒绝次数，供 FunctionCounter 读取。
 */
public class CountingRejectionHandler implements RejectedExecutionHandler {

    private final RejectedExecutionHandler delegate;
    private final AtomicLong rejectedCount = new AtomicLong();

    public CountingRejectionHandler(RejectedExecutionHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        rejectedCount.incrementAndGet();
        delegate.rejectedExecution(task, executor);
    }

    /** 累计拒绝次数 */
    public long rejectedCount() {
        return rejectedCount.get();
    }
}
