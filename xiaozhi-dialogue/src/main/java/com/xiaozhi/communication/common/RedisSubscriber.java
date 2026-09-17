package com.xiaozhi.communication.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.monitoring.CountingRejectionHandler;
import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.dialogue.audio.VadService;
import com.xiaozhi.dialogue.llm.factory.PersonaFactory;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpService;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.ai.stt.SttServiceFactory;
import com.xiaozhi.common.port.ProviderTokenClient;
import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.utils.JsonUtil;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import jakarta.annotation.Resource;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import lombok.extern.slf4j.Slf4j;
/**
 * Redis 消息订阅处理。
 * 监听跨实例广播，在本实例执行对应操作。
 * <p>本类只是业务逻辑的集合，不是纯配置类，用 @Component 而非 @Configuration，
 * 避免被 CGLIB 增强（本类也没有 @Bean 方法互相调用，无需要保留的场景）。
 */
@Slf4j
@Component
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
    private StorageServiceFactory storageServiceFactory;

    @Resource
    private ProviderTokenClient tokenClient;

    @Resource
    private ConfigService configService;

    @Resource
    private DeviceService deviceService;

    @Resource
    private PersonaFactory personaFactory;

    @Resource
    private ChatModelFactory chatModelFactory;

    @Resource
    private RoleService roleService;

    @Resource
    private VadService vadService;

    @Resource
    @Lazy
    private DeviceMcpService deviceMcpService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MeterRegistry meterRegistry;

    /**
     * 默认不设 taskExecutor 时，容器会退化成每条消息起一条 SimpleAsyncTaskExecutor 线程，
     * 广播量大时线程数没有上限；换成有界线程池做隔离和背压。只在 bean 方法里按需创建，
     * 避免脱离 Spring 容器直接 new RedisSubscriber()（如单测）时也起一堆线程。
     */
    private ThreadPoolTaskExecutor listenerTaskExecutor;

    // 队列满且线程已达上限时才会触发；计数后仍按 AbortPolicy 原样抛异常，行为不变
    private final CountingRejectionHandler listenerRejectionHandler =
            new CountingRejectionHandler(new ThreadPoolExecutor.AbortPolicy());

    @PreDestroy
    public void shutdownListenerTaskExecutor() {
        if (listenerTaskExecutor != null) {
            listenerTaskExecutor.shutdown();
        }
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        listenerTaskExecutor = new ThreadPoolTaskExecutor();
        listenerTaskExecutor.setCorePoolSize(2);
        listenerTaskExecutor.setMaxPoolSize(8);
        listenerTaskExecutor.setQueueCapacity(200);
        listenerTaskExecutor.setThreadNamePrefix("redis-sub-");
        listenerTaskExecutor.setRejectedExecutionHandler(listenerRejectionHandler);
        listenerTaskExecutor.initialize();
        container.setTaskExecutor(listenerTaskExecutor);

        ExecutorServiceMetrics.monitor(meterRegistry, listenerTaskExecutor.getThreadPoolExecutor(),
                "redis-listener", "xiaozhi.redis.listener", Tags.empty());
        FunctionCounter.builder("xiaozhi.redis.listener.executor.rejected", listenerRejectionHandler,
                        CountingRejectionHandler::rejectedCount)
                .description("Redis 订阅监听线程池拒绝任务次数（8 线程 + 200 队列已满）")
                .register(meterRegistry);

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
                RoleBO role = roleService.getBO(freshDevice.getRoleId());
                if (role != null) {
                    session.setInactiveTimeoutSeconds(role.getInactiveTimeoutSeconds() != null
                            ? role.getInactiveTimeoutSeconds() : 60);
                }
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
     * 角色属性变更（如音色、VAD 阈值）：遍历本实例 session，清理使用该角色的 Persona 并刷新 VAD 阈值快照。
     * VAD 阈值是 initSession 时取的快照，不在这里刷新就要等下一次 listen/start 才生效。
     */
    public void onRoleUpdated(String message) {
        try {
            Integer roleId = Integer.parseInt(message.trim());
            RoleBO updatedRole = roleService.getBO(roleId);
            int count = 0;
            for (ChatSession session : sessionManager.getAllSessions()) {
                DeviceBO device = session.getDevice();
                if (device != null && roleId.equals(device.getRoleId())) {
                    if (updatedRole != null) {
                        session.setInactiveTimeoutSeconds(updatedRole.getInactiveTimeoutSeconds() != null
                                ? updatedRole.getInactiveTimeoutSeconds() : 60);
                    }
                    Persona persona = session.getPersona();
                    if (persona != null) {
                        persona.getConversation().clear();
                        session.setPersona(null);
                        count++;
                    }
                    vadService.refreshRoleThresholds(session.getSessionId());
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
     * 关闭设备会话：只有设备在本实例时才处理。
     * excludeSessionId 命中时跳过——新连接建立过程中可能先于 registerDevice 收到自己发出的
     * 幽灵会话清理广播，此时本地还查不到设备；若时序不巧晚到，得靠这个字段而不是查询时序来避免误关新连接
     */
    public void onCloseSession(String message) {
        String deviceId = message;
        String excludeSessionId = null;
        try {
            Map<String, Object> payload = JsonUtil.fromJson(message, new TypeReference<>() {});
            if (payload != null && payload.containsKey("deviceId")) {
                deviceId = (String) payload.get("deviceId");
                excludeSessionId = (String) payload.get("excludeSessionId");
            }
        } catch (Exception e) {
            // 兼容旧格式：payload 直接就是 deviceId 字符串
        }
        if (deviceId == null) {
            return;
        }
        ChatSession session = sessionManager.getSessionByDeviceId(deviceId);
        if (session != null && !session.getSessionId().equals(excludeSessionId)) {
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

            // OSS 默认配置切换：清空存储工厂缓存，下次按最新配置重建（不依赖 configId 对应记录是否存在）
            if ("oss".equals(configType)) {
                storageServiceFactory.refresh();
                log.info("已清除存储工厂缓存 - configType: oss, configId: {}", configId);
                return;
            }

            ConfigBO config = configService.getBO(configId);
            if (config != null) {
                if ("stt".equals(configType)) {
                    sttServiceFactory.removeCache(config);
                } else if ("tts".equals(configType)) {
                    ttsServiceFactory.removeCache(config);
                } else if ("llm".equals(configType)) {
                    personaFactory.clearPersonasByModelId(configId);
                    chatModelFactory.removeCache(configId);
                }
                // Token 缓存（Coze OAuth、阿里云 Token 等）与 configType 无关，统一清除
                tokenClient.removeCache(config);
                log.info("已清除工厂缓存 - configType: {}, configId: {}", configType, configId);
            }
        } catch (Exception e) {
            log.error("处理 configChanged 广播失败", e);
        }
    }
}
