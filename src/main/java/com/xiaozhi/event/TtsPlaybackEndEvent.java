package com.xiaozhi.event;

import com.xiaozhi.communication.common.ChatSession;
import org.springframework.context.ApplicationEvent;

/**
 * TTS 播放结束事件
 * 由 Player.sendStop() 触发，用于通知各组件 TTS 播放已结束。
 * VadService 监听此事件以重置 Silero 隐状态，消除 TTS 播放期间的状态污染。
 */
public class TtsPlaybackEndEvent extends ApplicationEvent {

    public TtsPlaybackEndEvent(Object source) {
        super(source);
    }

    public ChatSession getSession() {
        return (ChatSession) getSource();
    }
}
