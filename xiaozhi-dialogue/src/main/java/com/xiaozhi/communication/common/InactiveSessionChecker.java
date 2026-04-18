package com.xiaozhi.communication.common;

import com.xiaozhi.communication.server.websocket.WebSocketSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
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

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Resource
    private SessionManager sessionManager;

    @Resource
    private DeviceRegistry deviceRegistry;

    @Value("${xiaozhi.check.inactive.session:true}")
    private boolean checkInactiveSession;

    @Value("${inactive.timeout.seconds:60}")
    private int inactiveTimeOutSeconds;

    @PostConstruct
    public void init() {
        if (checkInactiveSession) {
            scheduler.scheduleAtFixedRate(this::checkInactiveSessions, 10, 10, TimeUnit.SECONDS);
            log.info("不活跃会话检查任务已启动，超时时间: {}秒", inactiveTimeOutSeconds);
        }
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

    private void checkInactiveSessions() {
        Instant now = Instant.now();
        sessionManager.getAllSessions().forEach(session -> {
            // 刷新设备-实例心跳
            if (session.getDevice() != null) {
                deviceRegistry.refresh(session.getDevice().getDeviceId());
            }
            if (session instanceof WebSocketSession || session.isAudioChannelOpen()) {
                Instant lastActivity = session.getLastActivityTime();
                if (lastActivity != null) {
                    Duration inactiveDuration = Duration.between(lastActivity, now);
                    if (inactiveDuration.getSeconds() > inactiveTimeOutSeconds) {
                        // 正在说话或思考时不触发超时（SPEAKING/THINKING 有活跃处理）
                        // IDLE 和 LISTENING 均可触发（设备连接中但用户长时间没说话）
                        if (session.getDeviceState() != DeviceState.SPEAKING
                                && session.getDeviceState() != DeviceState.THINKING) {
                            log.info("会话 {} 已经 {} 秒没有有效活动，发送超时提示并自动关闭",
                                    session.getSessionId(), inactiveDuration.getSeconds());
                            session.clearAudioSinks();
                            if (session.getPersona() != null) {
                                session.getPersona().sendGoodbyeMessage();
                            }
                            if (session instanceof WebSocketSession) {
                                sessionManager.closeSession(session);
                            }
                        }
                    }
                }
            }
        });
    }
}
