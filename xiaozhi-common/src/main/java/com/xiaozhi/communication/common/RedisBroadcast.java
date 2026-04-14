package com.xiaozhi.communication.common;

import com.xiaozhi.event.AiConfigChangedEvent;
import com.xiaozhi.event.ConversationHistoryClearedEvent;
import com.xiaozhi.event.DeviceRoleChangedEvent;
import com.xiaozhi.event.DeviceSessionClosedEvent;
import com.xiaozhi.event.DeviceUpdatedEvent;
import com.xiaozhi.event.RoleUpdatedEvent;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 跨实例消息广播。
 * 通过 Redis Pub/Sub 通知所有实例执行对应操作，支持：
 * <ul>
 *   <li>clearConversation：清除指定设备的对话历史</li>
 *   <li>roleChanged：设备角色变更，重新加载 Persona</li>
 *   <li>configChanged：配置变更，清除对应工厂缓存</li>
 * </ul>
 */
@Component
public class RedisBroadcast {

    private static final Logger logger = LoggerFactory.getLogger(RedisBroadcast.class);

    public static final String CHANNEL_CLEAR_CONVERSATION = "xiaozhi:clear-conversation";
    public static final String CHANNEL_ROLE_CHANGED = "xiaozhi:role-changed";
    public static final String CHANNEL_CONFIG_CHANGED = "xiaozhi:config-changed";
    public static final String CHANNEL_CLOSE_SESSION = "xiaozhi:close-session";
    public static final String CHANNEL_ROLE_UPDATED = "xiaozhi:role-updated";
    public static final String CHANNEL_DEVICE_UPDATED = "xiaozhi:device-updated";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 事件驱动：收到对话清除事件后通过 Redis 广播
     */
    @EventListener
    public void onConversationClear(ConversationHistoryClearedEvent event) {
        clearConversation(event.getDeviceId());
    }

    @EventListener
    public void onDeviceRoleChanged(DeviceRoleChangedEvent event) {
        roleChanged(event.getDeviceId());
    }

    @EventListener
    public void onDeviceSessionClosed(DeviceSessionClosedEvent event) {
        closeDeviceSession(event.getDeviceId());
    }

    @EventListener
    public void onAiConfigChanged(AiConfigChangedEvent event) {
        configChanged(event.getConfigType(), event.getConfigId());
    }

    @EventListener
    public void onRoleUpdated(RoleUpdatedEvent event) {
        roleUpdated(event.getRoleId());
    }

    @EventListener
    public void onDeviceUpdated(DeviceUpdatedEvent event) {
        if (event.getDevice() != null && event.getDevice().getDeviceId() != null) {
            deviceUpdated(event.getDevice().getDeviceId());
        }
    }

    public void clearConversation(String deviceId) {
        publish(CHANNEL_CLEAR_CONVERSATION, deviceId);
    }

    public void roleChanged(String deviceId) {
        publish(CHANNEL_ROLE_CHANGED, deviceId);
    }

    public void closeDeviceSession(String deviceId) {
        publish(CHANNEL_CLOSE_SESSION, deviceId);
    }

    public void roleUpdated(Integer roleId) {
        publish(CHANNEL_ROLE_UPDATED, String.valueOf(roleId));
    }

    public void deviceUpdated(String deviceId) {
        publish(CHANNEL_DEVICE_UPDATED, deviceId);
    }

    public void configChanged(String configType, Integer configId) {
        String payload = JsonUtil.toJson(Map.of("configType", configType, "configId", configId));
        publish(CHANNEL_CONFIG_CHANGED, payload);
    }

    private void publish(String channel, String message) {
        try {
            stringRedisTemplate.convertAndSend(channel, message);
            logger.debug("已广播消息 - channel: {}, message: {}", channel, message);
        } catch (Exception e) {
            logger.error("广播消息失败 - channel: {}, message: {}", channel, message, e);
        }
    }
}
