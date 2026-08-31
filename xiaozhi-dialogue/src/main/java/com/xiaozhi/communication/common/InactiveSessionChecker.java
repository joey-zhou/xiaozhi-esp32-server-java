package com.xiaozhi.communication.common;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import com.xiaozhi.enums.DeviceState;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
/**
 * 不活跃会话检查器，定期检查并关闭超时未活动的会话。
 * 从 SessionManager 拆分出来，职责单一化。
 */
@Slf4j
@Component
public class InactiveSessionChecker {

    private static final int INACTIVE_CHECK_INTERVAL_SECONDS = 2;
    private static final int DEVICE_REGISTRY_REFRESH_INTERVAL_SECONDS = 60;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Resource
    private SessionManager sessionManager;

    @Resource
    private DeviceRegistry deviceRegistry;

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::checkInactiveSessions,
                INACTIVE_CHECK_INTERVAL_SECONDS, INACTIVE_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::refreshDeviceRegistry,
                DEVICE_REGISTRY_REFRESH_INTERVAL_SECONDS, DEVICE_REGISTRY_REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("不活跃会话检查任务已启动，检查间隔: {}秒，设备注册续期间隔: {}秒",
                INACTIVE_CHECK_INTERVAL_SECONDS, DEVICE_REGISTRY_REFRESH_INTERVAL_SECONDS);
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("不活跃会话检查任务已关闭");
    }

    void checkInactiveSessions() {
        Instant now = Instant.now();
        sessionManager.getAllSessions().forEach(session -> {
            try {
                checkInactiveSession(session, now);
            } catch (Exception e) {
                log.error("检查不活跃会话失败 - SessionId: {}", session.getSessionId(), e);
            }
        });
    }

    private void checkInactiveSession(ChatSession session, Instant now) {
        int timeoutSeconds = session.getInactiveTimeoutSeconds();
        if (timeoutSeconds <= 0 || !session.isAudioChannelOpen()) {
            return;
        }

        Instant lastActivity = session.getLastActivityTime();
        if (lastActivity == null) {
            return;
        }

        Duration inactiveDuration = Duration.between(lastActivity, now);
        if (inactiveDuration.compareTo(Duration.ofSeconds(timeoutSeconds)) < 0) {
            return;
        }

        // 正在说话或思考时不触发超时；IDLE 和 LISTENING 均可触发。
        if (session.getDeviceState() == DeviceState.SPEAKING
                || session.getDeviceState() == DeviceState.THINKING
                || !session.tryBeginInactiveClose()) {
            return;
        }

        log.info("会话 {} 已经 {} 秒没有有效活动，发送超时提示并自动关闭",
                session.getSessionId(), inactiveDuration.getSeconds());
        session.clearAudioSinks();

        var persona = session.getPersona();
        if (persona != null && session.isAudioChannelOpen()) {
            try {
                persona.sendGoodbyeMessage();
                return;
            } catch (Exception e) {
                log.warn("会话 {} 发送超时提示失败，直接关闭会话", session.getSessionId(), e);
            }
        }
        sessionManager.closeSession(session);
    }

    void refreshDeviceRegistry() {
        sessionManager.getAllSessions().forEach(session -> {
            try {
                if (session.getDevice() != null && session.getDevice().getDeviceId() != null) {
                    deviceRegistry.refresh(session.getDevice().getDeviceId());
                }
            } catch (Exception e) {
                log.error("刷新设备注册心跳失败 - SessionId: {}", session.getSessionId(), e);
            }
        });
    }
}
