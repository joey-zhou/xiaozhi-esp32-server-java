package com.xiaozhi.communication.server.websocket;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.communication.common.MessageHandler;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketProtocolVersionTest {

    private static final String SESSION_ID = "session-1";
    private static final byte[] OPUS = {0x0A, 0x0B, 0x0C};

    @Mock
    private SessionManager sessionManager;
    @Mock
    private MessageHandler messageHandler;
    @Mock
    private DeviceMcpService deviceMcpService;
    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;

    @InjectMocks
    private WebSocketHandler handler;

    private WebSocketSession chatSession;

    @BeforeEach
    void setUp() {
        lenient().when(springSession.getId()).thenReturn(SESSION_ID);
        chatSession = new WebSocketSession(springSession);
        chatSession.setDevice(new DeviceBO());
        lenient().when(sessionManager.getSession(SESSION_ID)).thenReturn(chatSession);
    }

    @Test
    void helloWithVersionSetsSessionProtocolAndEchoesIt() throws Exception {
        handler.handleTextMessage(springSession, new TextMessage("{\"type\":\"hello\",\"version\":2}"));

        assertThat(chatSession.getProtocolVersion()).isEqualTo(2);
        ArgumentCaptor<org.springframework.web.socket.WebSocketMessage<?>> captor =
                ArgumentCaptor.forClass(org.springframework.web.socket.WebSocketMessage.class);
        verify(springSession).sendMessage(captor.capture());
        assertThat((String) captor.getValue().getPayload()).contains("\"version\":2");
    }

    @Test
    void helloWithoutVersionKeepsV1() throws Exception {
        handler.handleTextMessage(springSession, new TextMessage("{\"type\":\"hello\"}"));

        assertThat(chatSession.getProtocolVersion()).isEqualTo(1);
    }

    @Test
    void helloWithoutVersionKeepsHandshakeNegotiatedVersion() throws Exception {
        // 握手头已协商 v2，hello 未带 version 时不应被降级
        chatSession.setProtocolVersion(2);

        handler.handleTextMessage(springSession, new TextMessage("{\"type\":\"hello\"}"));

        assertThat(chatSession.getProtocolVersion()).isEqualTo(2);
        ArgumentCaptor<org.springframework.web.socket.WebSocketMessage<?>> captor =
                ArgumentCaptor.forClass(org.springframework.web.socket.WebSocketMessage.class);
        verify(springSession).sendMessage(captor.capture());
        assertThat((String) captor.getValue().getPayload()).contains("\"version\":2");
    }

    @Test
    void helloWithUnsupportedVersionFallsBackToV1() throws Exception {
        handler.handleTextMessage(springSession, new TextMessage("{\"type\":\"hello\",\"version\":9}"));

        assertThat(chatSession.getProtocolVersion()).isEqualTo(1);
    }

    @Test
    void v2FrameDeliversPayloadAndTimestamp() {
        chatSession.setProtocolVersion(2);
        byte[] frame = BinaryProtocolCodec.encode(2, OPUS, 0x12345678L);

        handler.handleBinaryMessage(springSession, new BinaryMessage(ByteBuffer.wrap(frame)));

        verify(messageHandler).handleBinaryMessage(SESSION_ID, OPUS, 0x12345678L);
    }

    @Test
    void v1FrameDeliversRawPayloadWithZeroTimestamp() {
        handler.handleBinaryMessage(springSession, new BinaryMessage(ByteBuffer.wrap(OPUS)));

        verify(messageHandler).handleBinaryMessage(SESSION_ID, OPUS, 0L);
    }

    @Test
    void malformedV2FrameDowngradesSessionAndStillDeliversAudio() {
        chatSession.setProtocolVersion(2);
        // 设备声明 v2 却发了裸 opus
        byte[] rawOpus = new byte[40];
        rawOpus[15] = (byte) 250;

        handler.handleBinaryMessage(springSession, new BinaryMessage(ByteBuffer.wrap(rawOpus)));

        assertThat(chatSession.getProtocolVersion()).isEqualTo(1);
        verify(messageHandler).handleBinaryMessage(SESSION_ID, rawOpus, 0L);
    }
}
