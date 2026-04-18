package com.xiaozhi.communication.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.ai.stt.SttServiceFactory;
import com.xiaozhi.token.TokenService;
import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
/**
 * Redis 消息订阅配置。
 * 监听跨实例广播，在本实例执行对应操作。
 */
@Slf4j
@Configuration
public class RedisSubscriber {

    @Resource
    private SessionManager sessionManager;

    @Resource
    private DeviceRegistry deviceRegistry;

    @Resource
    private SttServiceFactory sttServiceFactory;

    @Resource
    private TtsServiceFactory ttsServiceFactory;

    @Resource
    private TokenService tokenService;

    @Resource
    private ConfigService configService;

    @Resource
    private DeviceService deviceService;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        addListener(container, "onClearConversation", RedisBroadcast.CHANNEL_CLEAR_CONVERSATION);
        addListener(container, "onRoleChanged", RedisBroadcast.CHANNEL_ROLE_CHANGED);
        addListener(container, "onConfigChanged", RedisBroadcast.CHANNEL_CONFIG_CHANGED);
        addListener(container, "onCloseSession", RedisBroadcast.CHANNEL_CLOSE_SESSION);
        addListener(container, "onRoleUpdated", RedisBroadcast.CHANNEL_ROLE_UPDATED);
        addListener(container, "onDeviceUpdated", RedisBroadcast.CHANNEL_DEVICE_UPDATED);

        return container;
    }

    private void addListener(RedisMessageListenerContainer container, String method, String channel) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(this, method);
        adapter.afterPropertiesSet();
        container.addMessageListener(adapter, new ChannelTopic(channel));
    }

    /**
     * 清除对话历史
     */
    public void onClearConversation(String deviceId) {
        sessionManager.findConversation(deviceId).ifPresent(conversation -> {
            conversation.clear();
            log.info("已清除设备对话历史（来自跨实例广播） - deviceId: {}", deviceId);
        });
    }

    /**
     * 设备角色变更：清理 Persona，下次唤醒时重新构建
     */
    public void onRoleChanged(String deviceId) {
        ChatSession session = sessionManager.getSessionByDeviceId(deviceId);
        if (session != null) {
            // 先从 DB 刷新 device（含新 roleId），否则重建 Persona 时仍用旧角色
            DeviceBO freshDevice = deviceService.getBO(deviceId);
            if (freshDevice != null) {
                freshDevice.setSessionId(session.getSessionId());
                session.setDevice(freshDevice);
            }
            Persona persona = session.getPersona();
            if (persona != null) {
                persona.getConversation().clear();
                session.setPersona(null);
            }
            log.info("已清理设备 Persona（来自跨实例广播） - deviceId: {}", deviceId);
        }
    }

    /**
     * 角色属性变更（如音色）：遍历本实例 session，清理使用该角色的 Persona
     */
    public void onRoleUpdated(String message) {
        try {
            Integer roleId = Integer.parseInt(message.trim());
            int count = 0;
            for (ChatSession session : sessionManager.getAllSessions()) {
                DeviceBO device = session.getDevice();
                if (device != null && roleId.equals(device.getRoleId())) {
                    Persona persona = session.getPersona();
                    if (persona != null) {
                        persona.getConversation().clear();
                        session.setPersona(null);
                        count++;
                    }
                }
            }
            if (count > 0) {
                log.info("角色属性变更，已清理 {} 个 Persona（roleId: {}）", count, roleId);
            }
        } catch (Exception e) {
            log.error("处理 roleUpdated 广播失败", e);
        }
    }

    /**
     * 关闭设备会话：只有设备在本实例时才处理
     */
    public void onCloseSession(String deviceId) {
        ChatSession session = sessionManager.getSessionByDeviceId(deviceId);
        if (session != null) {
            sessionManager.closeSession(session);
            log.info("已关闭设备会话（来自跨实例广播） - deviceId: {}", deviceId);
        }
    }

    /**
     * 设备信息变更：刷新本实例中该设备的 session 数据
     */
    public void onDeviceUpdated(String deviceId) {
        ChatSession session = sessionManager.getSessionByDeviceId(deviceId);
        if (session != null) {
            DeviceBO freshDevice = deviceService.getBO(deviceId);
            if (freshDevice != null) {
                freshDevice.setSessionId(session.getSessionId());
                session.setDevice(freshDevice);
                log.info("已刷新设备信息（来自跨实例广播） - deviceId: {}", deviceId);
            }
        }
    }

    /**
     * 配置变更：清除对应工厂缓存（STT/TTS/Token）
     */
    public void onConfigChanged(String message) {
        try {
            Map<String, Object> payload = JsonUtil.fromJson(message, new TypeReference<>() {});
            String configType = (String) payload.get("configType");
            Integer configId = (Integer) payload.get("configId");

            ConfigBO config = configService.getBO(configId);
            if (config != null) {
                if ("stt".equals(configType)) {
                    sttServiceFactory.removeCache(config);
                } else if ("tts".equals(configType)) {
                    ttsServiceFactory.removeCache(config);
                }
                // Token 缓存（Coze OAuth、阿里云 Token 等）与 configType 无关，统一清除
                tokenService.removeCache(config);
                log.info("已清除工厂缓存 - configType: {}, configId: {}", configType, configId);
            }
        } catch (Exception e) {
            log.error("处理 configChanged 广播失败", e);
        }
    }
}
