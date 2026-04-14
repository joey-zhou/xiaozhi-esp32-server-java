package com.xiaozhi.dialogue.llm.handler;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.event.ChatSessionClosedEvent;
import jakarta.annotation.Resource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 会话关闭时清理 Persona 相关资源（Conversation 历史、Player 播放器）。
 */
@Component
public class PersonaCleanup {

    @Resource
    private SessionManager sessionManager;

    @EventListener
    public void handleSessionClose(ChatSessionClosedEvent event) {
        ChatSession session = sessionManager.getSession(event.getSessionId());
        Optional.ofNullable(session)
                .map(s -> s.getPersona())
                .map(Persona::getConversation)
                .ifPresent(Conversation::clear);

        Optional.ofNullable(session)
                .map(ChatSession::getPlayer)
                .ifPresent(Player::stop);
    }
}
