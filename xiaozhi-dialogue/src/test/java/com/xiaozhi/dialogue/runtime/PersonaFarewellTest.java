package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.Synthesizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退出话术由调用方给定，Persona 只负责播完再关会话：用户说再见给告别语，超时退出给超时提示语。
 * 两种退出都不过 LLM，都挂上收尾回调；音频通道已关时一个字都不发。
 */
@ExtendWith(MockitoExtension.class)
class PersonaFarewellTest {

    private static final String SESSION_ID = "s1";
    private static final String GOODBYE_TEXT = "好的，拜拜~有需要随时叫我哦！";
    private static final String TIMEOUT_TEXT = "你好像在忙别的事情，我先退下啦~";

    @Mock
    private SessionManager sessionManager;
    @Mock
    private Synthesizer synthesizer;
    @Mock
    private Player player;
    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;

    private WebSocketSession session;
    private Persona persona;

    @BeforeEach
    void setUp() {
        lenient().when(springSession.getId()).thenReturn(SESSION_ID);
        lenient().when(springSession.isOpen()).thenReturn(true);
        session = new WebSocketSession(springSession);
        when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        persona = Persona.builder()
                .sessionManager(sessionManager)
                .sessionId(SESSION_ID)
                .synthesizer(synthesizer)
                .player(player)
                .conversation(Conversation.of("device", 1, SESSION_ID, "role", 1))
                .build();
        session.setPersona(persona);
        session.setPlayer(player);
    }

    @Test
    void farewellSpeaksExactlyWhatCallerGave() {
        persona.sendFarewell(GOODBYE_TEXT);

        assertThat(synthesized()).isEqualTo(GOODBYE_TEXT);
        verify(player).setFunctionAfterChat(any());
    }

    @Test
    void timeoutFarewellIsNotRewrittenIntoAResponsiveGoodbye() {
        persona.sendFarewell(TIMEOUT_TEXT);

        // 超时场景没人说过再见，Persona 不得把话术换成应答式的告别语
        assertThat(synthesized()).isEqualTo(TIMEOUT_TEXT);
        verify(player).setFunctionAfterChat(any());
    }

    @Test
    void closedAudioChannelSpeaksNothing() {
        when(springSession.isOpen()).thenReturn(false);

        persona.sendFarewell(TIMEOUT_TEXT);

        verify(synthesizer, never()).synthesize(anyString());
        verify(player, never()).setFunctionAfterChat(any());
    }

    private String synthesized() {
        ArgumentCaptor<String> spoken = ArgumentCaptor.forClass(String.class);
        verify(synthesizer).synthesize(spoken.capture());
        return spoken.getValue();
    }
}
