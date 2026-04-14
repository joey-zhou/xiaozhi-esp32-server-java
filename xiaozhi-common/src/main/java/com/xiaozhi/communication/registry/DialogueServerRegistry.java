package com.xiaozhi.communication.registry;

import java.util.List;

/**
 * Dialogue服务器注册中心 — 用于横向扩展时的服务发现和负载均衡
 */
public interface DialogueServerRegistry {

    /**
     * 注册dialogue服务器实例
     */
    void register(DialogueServerInfo serverInfo);

    /**
     * 注销dialogue服务器实例
     */
    void unregister(String instanceId);

    /**
     * 心跳更新，延长TTL
     */
    void heartbeat(DialogueServerInfo serverInfo);

    /**
     * 获取所有可用的dialogue服务器
     */
    List<DialogueServerInfo> getAvailableServers();

    /**
     * 负载均衡选择一个dialogue服务器
     */
    DialogueServerInfo selectServer();
}
