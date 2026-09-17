package com.xiaozhi.common;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住线程的上下文类加载器不随创建者走：创建者换成别的加载器，派生出的线程拿到的仍是应用类加载器。
 */
class AppVirtualThreadsTest {

    @Test
    void threadsUseApplicationClassLoaderRegardlessOfCreator() throws Exception {
        ClassLoader appLoader = AppVirtualThreads.class.getClassLoader();
        CompletableFuture<ClassLoader> seen = new CompletableFuture<>();
        try (URLClassLoader foreign = new URLClassLoader(new URL[0], null)) {
            Thread creator = Thread.ofPlatform().unstarted(() -> {
                try (ExecutorService executor = AppVirtualThreads.newPerTaskExecutor("test-")) {
                    executor.submit(() -> seen.complete(Thread.currentThread().getContextClassLoader()));
                }
            });
            creator.setContextClassLoader(foreign);
            creator.start();
            creator.join();

            assertThat(seen.get()).isSameAs(appLoader).isNotSameAs(foreign);
        }
    }

    @Test
    void factoryNamesVirtualThreadsWithPrefix() {
        Thread thread = AppVirtualThreads.factory("worker-").newThread(() -> {});

        assertThat(thread.isVirtual()).isTrue();
        assertThat(thread.getName()).startsWith("worker-");
    }
}
