package com.xiaozhi.communication.common;

import com.xiaozhi.event.AiConfigChangedEvent;
import com.xiaozhi.event.ConversationHistoryClearedEvent;
import com.xiaozhi.event.DeviceRoleChangedEvent;
import com.xiaozhi.event.DeviceSessionClosedEvent;
import com.xiaozhi.event.DeviceUpdatedEvent;
import com.xiaozhi.event.RoleUpdatedEvent;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
/**
 * 跨实例消息广播。
 * 通过 Redis Pub/Sub 通知所有实例执行对应操作，支持：
 * <ul>
 *   <li>clearConversation：清除指定设备的对话历史</li>
 *   <li>roleChanged：设备角色变更，重新加载 Persona</li>
 *   <li>configChanged：配置变更，清除对应工厂缓存</li>
 * </ul>
 */
@Slf4j
@Component
public class RedisBroadcast {

    public static final String CHANNEL_CLEAR_CONVERSATION = "xiaozhi:clear-conversation";
    public static final String CHANNEL_ROLE_CHANGED = "xiaozhi:role-changed";
    public static final String CHANNEL_CONFIG_CHANGED = "xiaozhi:config-changed";
    public static final String CHANNEL_CLOSE_SESSION = "xiaozhi:close-session";
    public static final String CHANNEL_ROLE_UPDATED = "xiaozhi:role-updated";
    public static final String CHANNEL_DEVICE_UPDATED = "xiaozhi:device-updated";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 事件驱动：收到对话清除事件后通过 Redis 广播。
     * 本类六个监听器都在事务提交后触发，无事务上下文时由 fallbackExecution 直接触发。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConversationClear(ConversationHistoryClearedEvent event) {
        clearConversation(event.getDeviceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDeviceRoleChanged(DeviceRoleChangedEvent event) {
        roleChanged(event.getDeviceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDeviceSessionClosed(DeviceSessionClosedEvent event) {
        closeDeviceSession(event.getDeviceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAiConfigChanged(AiConfigChangedEvent event) {
        configChanged(event.getConfigType(), event.getConfigId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoleUpdated(RoleUpdatedEvent event) {
        roleUpdated(event.getRoleId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
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
        closeDeviceSession(deviceId, null);
    }

    /**
     * 关闭设备在其它实例上的会话。excludeSessionId 传新会话自己的 sessionId，
     * 接收方命中该 sessionId 时跳过，避免新连接建立过程中收到自己发出的广播而误关自己
     */
    public void closeDeviceSession(String deviceId, String excludeSessionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", deviceId);
        payload.put("excludeSessionId", excludeSessionId);
        publish(CHANNEL_CLOSE_SESSION, JsonUtil.toJson(payload));
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
            log.debug("已广播消息 - channel: {}, message: {}", channel, message);
        } catch (Exception e) {
            log.error("广播消息失败 - channel: {}, message: {}", channel, message, e);
        }
    }
}
