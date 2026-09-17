package com.xiaozhi.communication.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
/**
 * 实例标识持有者。
 * 优先使用配置的 {@code xiaozhi.instance.id}，未配置时自动生成（hostname + 随机后缀）。
 * Pod/进程重启即重新注册。
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
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            this.instanceId = host + "-" + suffix;
        }
        log.info("实例标识已生成: {}", instanceId);
    }

    public String getInstanceId() {
        return instanceId;
    }
}
