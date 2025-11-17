package com.xiaozhi.event;

import com.xiaozhi.communication.common.ChatSession;
import org.springframework.context.ApplicationEvent;

import java.time.Clock;

/**
 * 会话链接打开(注册)事件
 */
public class ChatSessionOpenEvent extends ApplicationEvent {

    public ChatSessionOpenEvent(Object source) {
        super(source);
    }

    public ChatSessionOpenEvent(Object source, Clock clock) {
        super(source, clock);
    }

    public ChatSession getSession() {
        return (ChatSession) getSource();
    }

}
