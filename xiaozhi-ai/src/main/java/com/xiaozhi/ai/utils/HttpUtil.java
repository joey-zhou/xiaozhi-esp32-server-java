package com.xiaozhi.ai.utils;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Http工具类
 * 用于创建OkHttpClient实例
 */
public class HttpUtil {

    /**
     * Dispatcher 允许同时在途的异步请求数。
     * 每条存活的 provider WebSocket 会长期占用一个在途槽位（loopReader 阻塞到连接关闭后才 finished），
     * 该值即进程内 STT/TTS 长连接总数的硬上限，不能用默认的 64。
     */
    private static final int MAX_REQUESTS = 4096;

    /**
     * 单 host 同时在途的异步请求数，需大于单个云厂商可能持有的长连接数。
     */
    private static final int MAX_REQUESTS_PER_HOST = 2048;

    /**
     * 短查询的读超时。超过它即视为后端不可用，不再干等共享 client 的 60 秒。
     */
    private static final int QUERY_READ_TIMEOUT_SECONDS = 5;

    /**
     * OkHttpClient实例
     */
    public static final OkHttpClient client;

    /**
     * 短查询专用实例：与 {@link #client} 共用 dispatcher 和连接池，只把读超时收紧到 5 秒。
     * 只给毫秒级应答的小请求用，长连接、大批量写仍走 {@link #client}。
     */
    public static final OkHttpClient queryClient;

    static {
        ExecutorService dispatcherExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("okhttp-dispatcher-", 0).factory());
        Dispatcher dispatcher = new Dispatcher(dispatcherExecutor);
        dispatcher.setMaxRequests(MAX_REQUESTS);
        dispatcher.setMaxRequestsPerHost(MAX_REQUESTS_PER_HOST);

        client = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(new ConnectionPool(256, 5, TimeUnit.MINUTES))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        queryClient = client.newBuilder()
                .readTimeout(QUERY_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }
}
