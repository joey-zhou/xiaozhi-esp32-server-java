package com.xiaozhi.common;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 上下文类加载器固定为应用类加载器的虚拟线程工厂。
 * <p>
 * 新线程默认继承创建者的上下文类加载器。JDK HttpClient 的回调线程把它设成了系统类加载器，
 * 以 fat jar 方式运行时系统类加载器看不到 BOOT-INF/lib，从这种线程派生出去的线程再按 classpath
 * 找资源就会失败，比如 Spring AI 的 AbstractEmbeddingModel 静态初始化直接报「properties file must exist」。
 * 凡是会从库回调线程派生出去、又要跑业务代码的线程，都从这里创建。
 */
public final class AppVirtualThreads {

    private static final ClassLoader APP_CLASS_LOADER = AppVirtualThreads.class.getClassLoader();

    private AppVirtualThreads() {
    }

    /**
     * @param prefix 线程名前缀，后面跟递增序号
     */
    public static ThreadFactory factory(String prefix) {
        ThreadFactory delegate = Thread.ofVirtual().name(prefix, 0).factory();
        return task -> {
            Thread thread = delegate.newThread(task);
            thread.setContextClassLoader(APP_CLASS_LOADER);
            return thread;
        };
    }

    /**
     * 每个任务一条虚拟线程的执行器。
     */
    public static ExecutorService newPerTaskExecutor(String prefix) {
        return Executors.newThreadPerTaskExecutor(factory(prefix));
    }
}
