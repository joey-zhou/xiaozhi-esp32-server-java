package com.xiaozhi.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 回滚不了的副作用（如删文件）等事务提交之后再做，事务回滚时就不做
 */
public final class AfterCommit {

    private AfterCommit() {}

    /** 没有事务上下文时直接执行 */
    public static void run(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
