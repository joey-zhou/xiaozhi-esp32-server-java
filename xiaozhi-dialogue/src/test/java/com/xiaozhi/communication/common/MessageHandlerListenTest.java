package com.xiaozhi.communication.common;

import com.xiaozhi.communication.domain.ListenMessage;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.DialogueService;
import com.xiaozhi.dialogue.audio.AecService;
import com.xiaozhi.dialogue.audio.VadService;
import com.xiaozhi.dialogue.llm.factory.PersonaFactory;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.enums.ListenState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住 listen 消息的方法级分支：收句与取消聆听的判定条件必须逐条成立。
 *
 * <p>协议链路层面的 listen 状态机由 communication/protocol/ListenStateMachineTest 覆盖，
 * 本类补的是那边跑不到的分支：stop 不带 mode 时不能把本轮模式抹成 null（MessageHandler:440）、
 * 非 LISTENING 或非 manual 的 stop 一律走取消聆听、以及 Player 为空时不能中断消息处理。
 */
@ExtendWith(MockitoExtension.class)
class MessageHandlerListenTest {

    private static final String SESSION_ID = "listen-session";

    @Mock
    private SessionManager sessionManager;
    @Mock
    private VadService vadService;
    @Mock
    private DialogueService dialogueService;
    @Mock
    private AecService aecService;
    @Mock
    private PersonaFactory personaFactory;
    @Mock
    private MessageSender messageService;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private Player player;
    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;

    private MessageHandler messageHandler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        messageHandler = new MessageHandler();
        ReflectionTestUtils.setField(messageHandler, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(messageHandler, "vadService", vadService);
        ReflectionTestUtils.setField(messageHandler, "dialogueService", dialogueService);
        ReflectionTestUtils.setField(messageHandler, "aecService", aecService);
        ReflectionTestUtils.setField(messageHandler, "personaFactory", personaFactory);
        ReflectionTestUtils.setField(messageHandler, "messageService", messageService);
        ReflectionTestUtils.setField(messageHandler, "applicationContext", applicationContext);

        lenient().when(springSession.getId()).thenReturn(SESSION_ID);
        session = new WebSocketSession(springSession);
        session.setPlayer(player);
        lenient().when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
    }

    @Test
    void stopWithoutModeKeepsManualModeAndCompletesSegment() {
        when(vadService.finishSegment(SESSION_ID)).thenReturn(true);
        messageHandler.handleMessage(listen(ListenState.Start, ListenMode.Manual), SESSION_ID);

        // 设备松手时发的 stop 不带 mode，无条件赋值会把本轮模式抹成 null
        messageHandler.handleMessage(listen(ListenState.Stop, null), SESSION_ID);

        assertThat(session.getMode()).isEqualTo(ListenMode.Manual);
        verify(vadService).initSession(SESSION_ID, false);
        verify(dialogueService).completeSpeechSegment(session);
        // 收句不能关流也不能重置 VAD，STT 之后还要读本轮 pcm
        verify(vadService, never()).resetSession(SESSION_ID);
    }

    @Test
    void stopWhileNotListeningCancelsListening() {
        session.setMode(ListenMode.Manual);
        session.transitionTo(DeviceState.SPEAKING);
        session.createAudioStream();

        messageHandler.handleMessage(listen(ListenState.Stop, null), SESSION_ID);

        assertThat(session.getAudioSinks()).isNull();
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.IDLE);
        verify(vadService).resetSession(SESSION_ID);
        // 状态判定在前，本轮压根没在聆听就不该去问 VAD 要收句结果
        verify(vadService, never()).finishSegment(SESSION_ID);
        verify(dialogueService, never()).completeSpeechSegment(any());
    }

    @Test
    void autoModeStopCancelsListeningWithoutCompletingSegment() {
        session.setMode(ListenMode.Auto);
        session.transitionTo(DeviceState.LISTENING);
        session.createAudioStream();

        messageHandler.handleMessage(listen(ListenState.Stop, null), SESSION_ID);

        assertThat(session.getDeviceState()).isEqualTo(DeviceState.IDLE);
        verify(vadService).resetSession(SESSION_ID);
        verify(vadService, never()).finishSegment(SESSION_ID);
        verify(dialogueService, never()).completeSpeechSegment(any());
    }

    @Test
    void listenIsIgnoredWhilePlayerHasPendingFunction() {
        // 告别语等收尾回调播放中，listen 一律丢弃
        when(player.getFunctionAfterChat()).thenReturn(() -> {
        });

        messageHandler.handleMessage(listen(ListenState.Start, ListenMode.Auto), SESSION_ID);

        assertThat(session.getMode()).isNull();
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.IDLE);
        verify(vadService, never()).initSession(any(), anyBoolean());
    }

    // Player 尚未创建（设备未绑定、MQTT 早到的 listen）或已被告别流程清空时，
    // 视同没有待执行回调继续按 state 处理，不能抛异常打爆消息处理线程
    @Test
    void listenBeforePlayerCreatedStillStartsListening() {
        session.setPlayer(null);

        messageHandler.handleMessage(listen(ListenState.Start, ListenMode.Auto), SESSION_ID);

        assertThat(session.getDeviceState()).isEqualTo(DeviceState.LISTENING);
        assertThat(session.getMode()).isEqualTo(ListenMode.Auto);
        verify(vadService).initSession(SESSION_ID, true);
    }

    // 告别语播完清空 player 之后设备再唤醒，唤醒词要照常送进对话链路
    @Test
    void wakeWordAfterGoodbyeClearedPlayerIsStillHandled() {
        session.setPlayer(null);
        ListenMessage message = listen(ListenState.Detect, null);
        message.setText("小智小智");

        messageHandler.handleMessage(message, SESSION_ID);

        verify(dialogueService).handleWakeWord(session, "小智小智");
    }

    @Test
    void detectInitializesAecBeforeWakeWord() {
        ListenMessage message = listen(ListenState.Detect, null);
        message.setText("小智小智");

        messageHandler.handleMessage(message, SESSION_ID);

        // AEC 必须在唤醒响应的 TTS 开始前就绪，否则首句回声进不了参考
        InOrder order = inOrder(aecService, dialogueService);
        order.verify(aecService).initSession(SESSION_ID);
        order.verify(dialogueService).handleWakeWord(session, "小智小智");
    }

    @Test
    void textInitializesAecBeforeHandlingText() {
        ListenMessage message = listen(ListenState.Text, null);
        message.setText("讲个笑话");

        messageHandler.handleMessage(message, SESSION_ID);

        InOrder order = inOrder(aecService, messageService, dialogueService);
        order.verify(aecService).initSession(SESSION_ID);
        order.verify(messageService).sendSttMessage(session, "讲个笑话");
        order.verify(dialogueService).handleText(any(), any());
    }

    private static ListenMessage listen(ListenState state, ListenMode mode) {
        ListenMessage message = new ListenMessage();
        message.setState(state);
        message.setMode(mode);
        return message;
    }
}
