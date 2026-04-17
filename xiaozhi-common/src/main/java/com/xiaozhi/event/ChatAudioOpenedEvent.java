package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;
import lombok.Getter;

/**
 * 音频通道已连接事件。
 * 在 WebSocketHandler 中，设备发送 hello 并完成音频通道握手后发布。
 *
 * <p>扩展点（当前无监听器）：
 * <ul>
 *   <li>欢迎语播报：音频通道就绪后向设备播放欢迎提示音</li>
 *   <li>监控打点：统计音频通道建立延迟（从 WebSocket 连接到音频就绪的时间差）</li>
 *   <li>VAD/AEC 预热：提前初始化音频处理组件，减少首次对话延迟</li>
 * </ul>
 */
@Getter
public class ChatAudioOpenedEvent extends AbstractDomainEvent {

    private final String sessionId;
    private final String deviceId;

    public ChatAudioOpenedEvent(Object source, String sessionId, String deviceId) {
        super(source);
        this.sessionId = sessionId;
        this.deviceId = deviceId;
    }
}
