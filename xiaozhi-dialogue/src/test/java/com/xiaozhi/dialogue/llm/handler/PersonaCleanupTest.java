package com.xiaozhi.dialogue.llm.handler;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.runtime.Persona;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 断开时按打断收尾（用户听到的部分进历史）再把剩下的轮次压进摘要；没有 Persona 时只停播放器 */
class PersonaCleanupTest {

    private final PersonaCleanup cleanup = new PersonaCleanup();

    @Test
    void disconnectInterruptsThenFlushes() {
        WebSocketSession session = new WebSocketSession("s1");
        Persona persona = mock(Persona.class);
        Conversation conversation = mock(Conversation.class);
        when(persona.getConversation()).thenReturn(conversation);
        session.setPersona(persona);

        cleanup.cleanup(session);

        InOrder order = inOrder(persona, conversation);
        order.verify(persona).interrupt();
        order.verify(conversation).flush();
    }

    @Test
    void withoutPersonaOnlyThePlayerStops() {
        WebSocketSession session = new WebSocketSession("s1");
        Player player = mock(Player.class);
        session.setPlayer(player);

        cleanup.cleanup(session);

        verify(player).stop();
    }
}
