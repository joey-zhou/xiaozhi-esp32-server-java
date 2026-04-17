package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;
import lombok.Getter;

/**
 * 会话连接打开（注册）事件。
 * 在 SessionManager.registerSession() 中，设备 WebSocket 连接注册后发布。
 *
 * <p>扩展点（当前无监听器）：
 * <ul>
 *   <li>连接审计日志：记录设备上线时间、来源 IP</li>
 *   <li>在线状态推送：通知管理后台实时刷新设备状态</li>
 *   <li>资源预分配：提前为该设备加载角色配置、TTS 资源等</li>
 * </ul>
 */
@Getter
public class ChatSessionOpenedEvent extends AbstractDomainEvent {

    private final String sessionId;
    private final String deviceId;

    public ChatSessionOpenedEvent(Object source, String sessionId, String deviceId) {
        super(source);
        this.sessionId = sessionId;
        this.deviceId = deviceId;
    }
}
