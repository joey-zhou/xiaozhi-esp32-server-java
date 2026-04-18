package com.xiaozhi.communication;

import com.xiaozhi.communication.common.InstanceIdHolder;
import com.xiaozhi.communication.registry.DialogueServerInfo;
import com.xiaozhi.communication.registry.DialogueServerRegistry;
import com.xiaozhi.storage.service.StorageServiceFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
/**
 * Dialogue服务器自动注册器 — 启动时注册，定时心跳，关闭时注销
 */
@Slf4j
@Component
public class DialogueServerRegistrar {

    @Resource
    private ServerAddressProvider serverAddressProvider;

    @Resource
    private DialogueServerRegistry dialogueServerRegistry;

    @Resource
    private InstanceIdHolder instanceIdHolder;

    @Resource
    private StorageServiceFactory storageServiceFactory;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void register() {
        String instanceId = instanceIdHolder.getInstanceId();
        try {
            dialogueServerRegistry.register(buildServerInfo());
            log.info("Dialogue服务器已注册到注册中心, instanceId={}", instanceId);
        } catch (Exception e) {
            log.warn("Dialogue服务器初次注册失败，将在后续心跳继续重试, instanceId={}", instanceId, e);
        }

        checkStorageConfig();

        // 每30秒发送心跳
        scheduler.scheduleAtFixedRate(() -> {
            try {
                dialogueServerRegistry.heartbeat(buildServerInfo());
            } catch (Exception e) {
                log.warn("Dialogue服务器心跳失败, instanceId={}", instanceId, e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void unregister() {
        scheduler.shutdown();
        try {
            String instanceId = instanceIdHolder.getInstanceId();
            dialogueServerRegistry.unregister(instanceId);
            log.info("Dialogue服务器已从注册中心注销, instanceId={}", instanceId);
        } catch (Exception e) {
            log.warn("注销失败", e);
        }
    }

    private void checkStorageConfig() {
        try {
            String provider = storageServiceFactory.getStorageService().getProvider();
            if ("local".equals(provider)) {
                log.warn("当前 StorageService 为 local 模式，音频文件仅存储在本地。集群部署请配置 COS/OSS，否则跨实例音频不可用。");
            }
        } catch (Exception e) {
            log.warn("检测 StorageService 配置失败: {}", e.getMessage());
        }
    }

    private DialogueServerInfo buildServerInfo() {
        DialogueServerInfo info = new DialogueServerInfo();
        info.setInstanceId(instanceIdHolder.getInstanceId());
        info.setWebsocketAddress(serverAddressProvider.getWebsocketAddress());
        info.setUdpAddress(serverAddressProvider.getUdpAddress());
        info.setOtaAddress(serverAddressProvider.getOtaAddress());
        info.setMcpAddress(serverAddressProvider.getMcpAddress());
        info.setServerAddress(serverAddressProvider.getServerAddress());
        return info;
    }
}
