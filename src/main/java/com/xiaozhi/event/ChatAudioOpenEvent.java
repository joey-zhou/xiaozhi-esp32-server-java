package com.xiaozhi.event;

import com.xiaozhi.communication.common.ChatSession;
import org.springframework.context.ApplicationEvent;

import java.time.Clock;

/**
 * 音频通道已连接事件
 */
public class ChatAudioOpenEvent extends ApplicationEvent {

    public ChatAudioOpenEvent(Object source) {
        super(source);
    }

    public ChatAudioOpenEvent(Object source, Clock clock) {
        super(source, clock);
    }

    public ChatSession getSession() {
        return (ChatSession) getSource();
    }

}
