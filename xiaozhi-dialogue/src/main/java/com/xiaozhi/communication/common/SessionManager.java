package com.xiaozhi.communication.common;

import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.device.domain.repository.DeviceRepository;
import com.xiaozhi.event.ChatAudioOpenedEvent;
import com.xiaozhi.event.ChatSessionClosedEvent;
import com.xiaozhi.event.DeviceOnlineEvent;
import com.xiaozhi.event.DeviceUpdatedEvent;
import com.xiaozhi.event.ChatSessionOpenedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 会话注册表，负责管理所有连接的会话状态。
 * 核心职责：register / get / remove / close，以及设备注册与验证码状态管理。
 * <p>
 * 不活跃会话检查已拆分至 {@link InactiveSessionChecker}。
 * 音频流管理已迁移至 {@link ChatSession} 实例方法。
 */
@Service
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final ConcurrentHashMap<String, ChatSession> sessions = new ConcurrentHashMap<>();

    /** deviceId → sessionId 反向索引，O(1) 查找设备所在会话 */
    private final ConcurrentHashMap<String, String> deviceIdToSessionId = new ConcurrentHashMap<>();

    // 存储验证码生成状态
    private final ConcurrentHashMap<String, Boolean> captchaState = new ConcurrentHashMap<>();

    // 用于启动时延迟重置设备状态
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // 服务关闭标志，关闭期间跳过设备状态写库（启动时会 bulk reset，无需重复写）
    private volatile boolean shuttingDown = false;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    @Lazy
    private DeviceRepository deviceRepository;

    @Resource
    private DeviceRegistry deviceRegistry;

    @Resource
    private InstanceIdHolder instanceIdHolder;

    @Value("${xiaozhi.check.inactive.session:true}")
    private boolean checkInactiveSession;

    @PostConstruct
    public void init() {
        if (checkInactiveSession) {
            // 项目启动时，只重置属于本实例的设备为离线（延迟执行避免循环依赖）
            scheduler.schedule(() -> {
                try {
                    Set<String> ownDeviceIds = deviceRegistry.getOwnDeviceIds();
                    if (!ownDeviceIds.isEmpty()) {
                        int updated = deviceRepository.batchUpdateState(ownDeviceIds, DeviceBO.DEVICE_STATE_OFFLINE);
                        logger.info("项目启动，重置本实例 {} 个设备状态为离线", updated);
                        // 清理本实例旧的 Redis 映射
                        for (String deviceId : ownDeviceIds) {
                            deviceRegistry.unbind(deviceId);
                        }
                    }
                    logger.info("项目启动，instanceId: {}", instanceIdHolder.getInstanceId());
                } catch (Exception e) {
                    logger.error("项目启动时重置设备状态失败", e);
                }
            }, 1, TimeUnit.SECONDS);
        }
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    /**
     * ContextClosedEvent 在所有 @PreDestroy 之前触发，
     * 确保 shuttingDown 标志在断链回调发生前已置位。
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        shuttingDown = true;
        scheduler.shutdown();
    }

    /**
     * 打开音频通道并发布事件（供Handler调用）
     */
    public void openAudioChannel(String sessionId, String deviceId) {
        applicationContext.publishEvent(new ChatAudioOpenedEvent(this, sessionId, deviceId));
    }

    /**
     * 设备信息变更时同步到对应的会话
     */
    @EventListener
    public void onDeviceUpdated(DeviceUpdatedEvent event) {
        DeviceBO device = event.getDevice();
        if (device == null || device.getDeviceId() == null) {
            return;
        }
        ChatSession session = getSessionByDeviceId(device.getDeviceId());
        if (session != null) {
            DeviceBO currentDevice = session.getDevice();
            if (currentDevice != null) {
                if (!StringUtils.hasText(device.getSessionId())) {
                    device.setSessionId(currentDevice.getSessionId());
                }
                if (!StringUtils.hasText(device.getRoleName())) {
                    device.setRoleName(currentDevice.getRoleName());
                }
            }
            session.setDevice(device);
        }
    }

    // ========== 会话注册与获取 ==========

    public void registerSession(String sessionId, ChatSession chatSession) {
        sessions.put(sessionId, chatSession);
        logger.info("会话已注册 - SessionId: {}  SessionType: {}", sessionId, chatSession.getClass().getSimpleName());
        String deviceId = chatSession.getDevice() != null ? chatSession.getDevice().getDeviceId() : null;
        applicationContext.publishEvent(new ChatSessionOpenedEvent(this, sessionId, deviceId));
    }

    public void removeSession(String sessionId) {
        ChatSession removed = sessions.remove(sessionId);
        if (removed != null && removed.getDevice() != null) {
            deviceIdToSessionId.remove(removed.getDevice().getDeviceId());
        }
    }

    public ChatSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public ChatSession getSessionByDeviceId(String deviceId) {
        String sessionId = deviceIdToSessionId.get(deviceId);
        if (sessionId != null) {
            ChatSession session = sessions.get(sessionId);
            if (session != null) {
                return session;
            }
            // 映射残留，清理
            deviceIdToSessionId.remove(deviceId);
        }
        return null;
    }

    /**
     * 获取所有会话（供 InactiveSessionChecker 等遍历使用）
     */
    public Collection<ChatSession> getAllSessions() {
        return sessions.values();
    }

    // ========== 会话关闭 ==========

    public void closeSession(String sessionId) {
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            closeSession(chatSession);
        }
    }

    public void closeSession(ChatSession chatSession) {
        if (chatSession == null) {
            return;
        }
        try {
            if (chatSession instanceof WebSocketSession) {
                removeSession(chatSession.getSessionId());
            }
            // 解除设备-实例绑定
            if (chatSession.getDevice() != null) {
                deviceRegistry.unbind(chatSession.getDevice().getDeviceId());
            }
            if (chatSession.isAudioChannelOpen()) {
                chatSession.close();
                String closeDeviceId = chatSession.getDevice() != null ? chatSession.getDevice().getDeviceId() : null;
                applicationContext.publishEvent(new ChatSessionClosedEvent(this, chatSession.getSessionId(), closeDeviceId));
                logger.info("会话已关闭 - SessionId: {} SessionType: {}", chatSession.getSessionId(), chatSession.getClass().getSimpleName());
            }
            chatSession.clearAudioSinks();
        } catch (Exception e) {
            logger.error("清理会话资源时发生错误 - SessionId: {}",
                    chatSession.getSessionId(), e);
        }
    }

    // ========== 设备注册 ==========

    public void registerDevice(String sessionId, DeviceBO device) {
        if (device == null || device.getDeviceId() == null) {
            logger.warn("注册设备失败: device 或 deviceId 为 null, sessionId={}", sessionId);
            return;
        }
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            chatSession.setDevice(device);
            deviceIdToSessionId.put(device.getDeviceId(), sessionId);
            updateLastActivity(sessionId);
            deviceRegistry.bind(device.getDeviceId());
            logger.debug("设备配置已注册 - SessionId: {}, DeviceId: {}", sessionId, device.getDeviceId());
            applicationContext.publishEvent(new DeviceOnlineEvent(this, device.getDeviceId()));
        }
    }

    public void updateLastActivity(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        if (session != null) {
            session.setLastActivityTime(java.time.Instant.now());
        }
    }

    // ========== 验证码状态 ==========

    public boolean markCaptchaGeneration(String deviceId) {
        return captchaState.putIfAbsent(deviceId, Boolean.TRUE) == null;
    }

    public void unmarkCaptchaGeneration(String deviceId) {
        captchaState.remove(deviceId);
    }

    // ========== 跨会话查询 ==========

    public Optional<Conversation> findConversation(String deviceId) {
        ChatSession session = getSessionByDeviceId(deviceId);
        if (session != null && session.getPersona() != null) {
            return Optional.ofNullable(session.getPersona().getConversation());
        }
        return Optional.empty();
    }
}
