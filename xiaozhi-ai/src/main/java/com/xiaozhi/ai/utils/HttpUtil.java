package com.xiaozhi.ai.utils;

import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

/**
 * Http工具类
 * 用于创建OkHttpClient实例
 */
public class HttpUtil {
    /**
     * OkHttpClient实例
     */
    public static final OkHttpClient client;

    static{
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
}
