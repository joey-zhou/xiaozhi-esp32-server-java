package com.xiaozhi.communication.common;

import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.common.port.DeviceWriter;
import com.xiaozhi.dialogue.audio.AecService;
import com.xiaozhi.dialogue.audio.VadService;
import com.xiaozhi.dialogue.llm.handler.PersonaCleanup;
import com.xiaozhi.event.ChatAudioOpenedEvent;
import com.xiaozhi.event.ChatSessionClosedEvent;
import com.xiaozhi.event.DeviceOnlineEvent;
import com.xiaozhi.event.DeviceUpdatedEvent;
import com.xiaozhi.event.ChatSessionOpenedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
/**
 * 会话注册表，负责管理所有连接的会话状态。
 * 核心职责：register / get / remove / close，以及设备注册管理。
 * <p>
 * 不活跃会话检查已拆分至 {@link InactiveSessionChecker}。
 * 音频流管理已迁移至 {@link ChatSession} 实例方法。
 */
@Slf4j
@Service
public class SessionManager {
    private final ConcurrentHashMap<String, ChatSession> sessions = new ConcurrentHashMap<>();

    /** deviceId → sessionId 反向索引，O(1) 查找设备所在会话 */
    private final ConcurrentHashMap<String, String> deviceIdToSessionId = new ConcurrentHashMap<>();

    // 存储验证码生成状态
    private final ConcurrentHashMap<String, Boolean> captchaState = new ConcurrentHashMap<>();

    /** 上一次运行遗留在本实例名下的设备，容器就绪后据此补偿状态 */
    private volatile Set<String> staleDeviceIds = Set.of();

    // 服务关闭标志，关闭期间跳过设备状态写库（启动时会 bulk reset，无需重复写）
    private volatile boolean shuttingDown = false;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    @Lazy
    private DeviceWriter deviceWriter;

    @Resource
    private DeviceRegistry deviceRegistry;

    // 音频服务反向依赖会话注册表，这里按需取代理避免构造期成环
    @Resource
    @Lazy
    private VadService vadService;

    @Resource
    @Lazy
    private AecService aecService;

    @Resource
    private InstanceIdHolder instanceIdHolder;

    @Resource
    private PersonaCleanup personaCleanup;

    @PostConstruct
    public void init() {
        // 快照取在容器开始接客之前：此刻还挂在本实例名下的，只可能是上一次运行的遗留
        try {
            staleDeviceIds = deviceRegistry.getOwnDeviceIds();
        } catch (Exception e) {
            log.error("读取本实例遗留设备失败", e);
        }
        log.info("项目启动，instanceId: {}, 本实例遗留设备 {} 个",
                instanceIdHolder.getInstanceId(), staleDeviceIds.size());
    }

    /**
     * 把上一次运行遗留的设备补写成离线并清掉旧路由。
     * 放在容器就绪后执行：DeviceWriter 是懒加载代理，在 @PostConstruct 里调用会把它的依赖链拉进
     * 构造期而成环。Web 容器那段更早的窗口则靠
     * deviceIdToSessionId 兜底——真连上来的设备已经登记在册，这里必须跳过，
     * 否则会把在线设备写成离线，还会解掉它刚建好的 device→instance 路由。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void resetStaleDevices() {
        try {
            Set<String> pending = new HashSet<>(staleDeviceIds);
            staleDeviceIds = Set.of();
            pending.removeAll(deviceIdToSessionId.keySet());
            if (pending.isEmpty()) {
                return;
            }
            int updated = deviceWriter.batchUpdateState(pending, DeviceBO.DEVICE_STATE_OFFLINE);
            log.info("项目启动，重置本实例 {} 个遗留设备状态为离线", updated);
            for (String deviceId : pending) {
                if (!deviceIdToSessionId.containsKey(deviceId)) {
                    deviceRegistry.unbind(deviceId);
                }
            }
        } catch (Exception e) {
            log.error("项目启动时重置设备状态失败", e);
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
    }

    /**
     * 打开音频通道（供Handler调用）
     */
    public void openAudioChannel(String sessionId, String deviceId) {
        ChatSession session = sessions.get(sessionId);
        if (session != null) {
            session.resetInactiveClosing();
        }
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
        log.info("会话已注册 - SessionId: {}  SessionType: {}", sessionId, chatSession.getClass().getSimpleName());
    }

    public void removeSession(String sessionId) {
        ChatSession removed = sessions.remove(sessionId);
        if (removed != null && removed.getDevice() != null) {
            // 只清自己建立的映射，设备重连后旧连接关闭不能删掉新会话的映射
            deviceIdToSessionId.remove(removed.getDevice().getDeviceId(), sessionId);
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
            // 映射残留，清理；只清自己读到的这份，避免误删并发场景下已更新的映射
            deviceIdToSessionId.remove(deviceId, sessionId);
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

    /**
     * 连接关闭时写入设备离线状态。服务关闭期间跳过，启动时会批量重置。
     */
    private void updateDeviceStateOnClose(ChatSession chatSession) {
        DeviceBO device = chatSession.getDevice();
        if (device == null || isShuttingDown()) {
            return;
        }
        String sessionId = chatSession.getSessionId();
        String deviceId = device.getDeviceId();
        String newState = DeviceBO.DEVICE_STATE_OFFLINE;
        Thread.startVirtualThread(() -> {
            try {
                // 设备已在新连接上重连时不覆盖新会话的状态
                ChatSession currentSession = getSessionByDeviceId(deviceId);
                if (currentSession != null && !sessionId.equals(currentSession.getSessionId())) {
                    return;
                }
                deviceWriter.updateState(deviceId, newState);
                log.info("连接已关闭 - SessionId: {}, DeviceId: {}, 新状态: {}", sessionId, deviceId, newState);
            } catch (Exception e) {
                log.error("更新设备状态失败", e);
            }
        });
    }

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
            // 状态写库要赶在会话被摘出注册表之前，否则设备主动 goodbye、超时关闭、
            // 退出意图这几条路径的连接回调都取不到会话，离线状态永远写不进去
            updateDeviceStateOnClose(chatSession);
            // 先断上游合成、停播放器，再往下释放音频资源。同样要在这里做而不是挂事件：
            // 硬断线时连接已关，事件发不出去，上游 LLM/TTS 订阅会一路跑到本轮结束
            personaCleanup.cleanup(chatSession);
            // VAD 状态与 AEC 的原生 APM 每条关闭路径都要释放：
            // WebSocket 的连接回调取不到已摘除的会话，留在回调里会漏掉超时告别、退出意图这些路径
            vadService.resetSession(chatSession.getSessionId());
            aecService.resetSession(chatSession.getSessionId());

            if (chatSession instanceof WebSocketSession) {
                removeSession(chatSession.getSessionId());
            }
            // 解除设备-实例绑定
            if (chatSession.getDevice() != null) {
                String deviceId = chatSession.getDevice().getDeviceId();
                ChatSession bound = getSessionByDeviceId(deviceId);
                // 设备已在本实例的新连接上重连时绑定归新会话，旧连接收尾不能解掉。
                // 跨实例重连本地映射查不到，只能靠 Redis 里的实例标识比对
                if (bound == null || bound.getSessionId().equals(chatSession.getSessionId())) {
                    deviceRegistry.unbindIfOwned(deviceId);
                }
            }
            if (chatSession.isAudioChannelOpen()) {
                chatSession.close();
                String closeDeviceId = chatSession.getDevice() != null ? chatSession.getDevice().getDeviceId() : null;
                applicationContext.publishEvent(new ChatSessionClosedEvent(this, chatSession.getSessionId(), closeDeviceId));
                log.info("会话已关闭 - SessionId: {} SessionType: {}", chatSession.getSessionId(), chatSession.getClass().getSimpleName());
            }
            chatSession.clearAudioSinks();
        } catch (Exception e) {
            log.error("清理会话资源时发生错误 - SessionId: {}",
                    chatSession.getSessionId(), e);
        }
    }

    // ========== 设备注册 ==========

    public void registerDevice(String sessionId, DeviceBO device) {
        if (device == null || device.getDeviceId() == null) {
            log.warn("注册设备失败: device 或 deviceId 为 null, sessionId={}", sessionId);
            return;
        }
        ChatSession chatSession = sessions.get(sessionId);
        if (chatSession != null) {
            chatSession.setDevice(device);
            deviceIdToSessionId.put(device.getDeviceId(), sessionId);
            updateLastActivity(sessionId);
            deviceRegistry.bind(device.getDeviceId());
            log.debug("设备配置已注册 - SessionId: {}, DeviceId: {}", sessionId, device.getDeviceId());
            applicationContext.publishEvent(new DeviceOnlineEvent(this, device.getDeviceId()));
        }
    }

    public void updateLastActivity(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        if (session != null) {
            session.setLastActivityTime(Instant.now());
        }
    }

    // ========== 跨会话查询 ==========

    public Optional<Conversation> findConversation(String deviceId) {
        ChatSession session = getSessionByDeviceId(deviceId);
        if (session != null && session.getPersona() != null) {
            return Optional.of(session.getPersona().getConversation());
        }
        return Optional.empty();
    }
}
