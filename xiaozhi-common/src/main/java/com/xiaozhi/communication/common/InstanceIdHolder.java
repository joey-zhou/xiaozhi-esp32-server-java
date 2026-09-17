package com.xiaozhi.communication.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

import lombok.extern.slf4j.Slf4j;
/**
 * 实例标识持有者。
 * 优先使用配置的 {@code xiaozhi.instance.id}，未配置时取 hostname 作为实例标识。
 * 同一个容器/Pod 崩溃后原地重启，hostname 不变，DeviceRegistry 记录的旧设备归属才能在启动时被正确
 * 识别并批量重置；只有容器被真正重建（换了新 hostname）才会生成新标识，此时旧的 Redis 映射会在
 * TTL 到期后自然过期，不需要靠这里的重置来清理。
 * 之前这里在 hostname 后面拼了随机后缀，导致每次重启标识都不一样，启动时的批量重置永远匹配不到任何设备。
 */
@Slf4j
@Component
public class InstanceIdHolder {

    private final String instanceId;

    public InstanceIdHolder(@Value("${xiaozhi.instance.id:}") String configuredInstanceId) {
        if (configuredInstanceId != null && !configuredInstanceId.isEmpty()) {
            this.instanceId = configuredInstanceId;
        } else {
            String host;
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                host = "unknown";
            }
            this.instanceId = host;
        }
        log.info("实例标识已生成: {}", instanceId);
    }

    public String getInstanceId() {
        return instanceId;
    }
}
