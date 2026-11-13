package com.xiaozhi.dialogue;

import com.xiaozhi.dialogue.runtime.FaultNotifier;
import com.xiaozhi.dialogue.event.AbortReason;
import com.xiaozhi.dialogue.llm.factory.PersonaFactory;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.dialogue.event.ChatAbortedEvent;
import com.xiaozhi.ai.llm.service.AddresseeClassifier;
import com.xiaozhi.ai.llm.service.IntentService;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.audio.AecService;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.runtime.Persona;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import com.xiaozhi.common.model.bo.DeviceBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 首字暂停后由终稿决定：回声、空、附和词走 <1ms 快路径直接续播丢弃，
 * 落到快路径之外的整句再交给模型判定是不是在对设备说，不是则续播，是才确认打断并收尾。
 */
@ExtendWith(MockitoExtension.class)
class DialogueServiceBargeInTest {

    @Mock
    private IntentService intentService;
    @Mock
    private AddresseeClassifier addresseeClassifier;
    @Mock
    private MessageSender messageService;
    @Mock
    private AecService aecService;
    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;
    @Mock
    private Player player;
    @Mock
    private Persona persona;

    @Mock
    private FaultNotifier faultNotifier;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PersonaFactory personaFactory;

    @InjectMocks
    private DialogueService dialogueService;

    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        lenient().when(springSession.getId()).thenReturn("s1");
        session = new WebSocketSession(springSession);
        session.setPlayer(player);
        session.setPersona(persona);
        DeviceBO device = new DeviceBO();
        device.setDeviceId("dev-1");
        session.setDevice(device);
        lenient().when(personaFactory.playerOf(session)).thenReturn(player);
    }

    @Test
    void backchannelResumesAndDropsUtterance() {
        when(intentService.isBackchannel("嗯嗯")).thenReturn(true);

        boolean proceed = dialogueService.resolveBargeIn(session, persona, "嗯嗯");

        assertThat(proceed).isFalse();
        verify(player).resume();
        verify(addresseeClassifier, never()).directedAtDevice(any(), any(), any(), any());
        verify(persona, never()).markInterrupted();
        verify(persona, never()).interrupt();
        verify(player, never()).abort();
    }

    @Test
    void affirmativeAnsweringQuestionIsRealSpeech() {
        when(player.spokenSentences()).thenReturn(List.of("要我现在设闹钟吗？"));
        when(addresseeClassifier.directedAtDevice(any(), any(), any(), eq("好的"))).thenReturn(true);

        boolean proceed = dialogueService.resolveBargeIn(session, persona, "好的");

        assertThat(proceed).isTrue();
        verify(intentService, never()).isBackchannel(any());
        verify(persona).markInterrupted();
        verify(player, never()).resume();
    }

    @Test
    void echoOfOwnSpeechResumes() {
        when(player.recentlySpoke("等我一下哈。")).thenReturn(true);

        boolean proceed = dialogueService.resolveBargeIn(session, persona, "等我一下哈。");

        assertThat(proceed).isFalse();
        verify(player).resume();
        verify(intentService, never()).isBackchannel(any());
        verify(addresseeClassifier, never()).directedAtDevice(any(), any(), any(), any());
        verify(persona, never()).markInterrupted();
    }

    @Test
    void emptyFinalResumes() {
        boolean proceed = dialogueService.resolveBargeIn(session, persona, "");

        assertThat(proceed).isFalse();
        verify(player).resume();
        verify(addresseeClassifier, never()).directedAtDevice(any(), any(), any(), any());
        verify(persona, never()).markInterrupted();
    }

    @Test
    void realSpeechConfirmsInterrupt() {
        when(intentService.isBackchannel("换一个")).thenReturn(false);
        when(addresseeClassifier.directedAtDevice(any(), any(), any(), eq("换一个"))).thenReturn(true);

        boolean proceed = dialogueService.resolveBargeIn(session, persona, "换一个");

        assertThat(proceed).isTrue();
        verify(persona).markInterrupted();
        verify(persona).interrupt();
        verify(player, never()).resume();
        verify(player).abort();
    }

    /** 直调打断，收尾后发一个事实事件 */
    @Test
    void confirmedInterruptIsPublishedAsAbortEvent() {
        when(addresseeClassifier.directedAtDevice(any(), any(), any(), eq("换一个"))).thenReturn(true);

        dialogueService.resolveBargeIn(session, persona, "换一个");

        ArgumentCaptor<ChatAbortedEvent> captor = ArgumentCaptor.forClass(ChatAbortedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getSessionId()).isEqualTo("s1");
        assertThat(captor.getValue().getDeviceId()).isEqualTo("dev-1");
        assertThat(captor.getValue().getReason()).isEqualTo(AbortReason.BARGE_IN);
    }

    /** 还没有 Persona 时：照样让播放器停播并告诉设备，清掉 AEC 参考 */
    @Test
    void abortWithoutAPersonaStillStopsThePlayer() {
        session.setPersona(null);

        dialogueService.abortDialogue(session, AbortReason.DEVICE_ABORT);

        verify(aecService).clearReference("s1");
        verify(player).abort();
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.LISTENING);
    }

    /** 插话打断时新一轮的识别流已经在跑：不关流、不切回聆听、不再递增一次代次 */
    @Test
    void bargeInKeepsTheNewRecognitionRunning() {
        session.transitionTo(DeviceState.THINKING);

        dialogueService.abortDialogue(session, AbortReason.BARGE_IN);

        verify(persona, never()).markInterrupted();
        verify(persona).interrupt();
        verify(player).abort();
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.THINKING);
    }

    @Test
    void utteranceAimedAtSomeoneElseResumesInsteadOfInterrupting() {
        when(intentService.isBackchannel("对，他这个逻辑有问题")).thenReturn(false);
        when(addresseeClassifier.directedAtDevice(any(), any(), any(), eq("对，他这个逻辑有问题")))
                .thenReturn(false);

        boolean proceed = dialogueService.resolveBargeIn(session, persona, "对，他这个逻辑有问题");

        assertThat(proceed).isFalse();
        verify(player).resume();
        verify(persona, never()).markInterrupted();
        verify(persona, never()).interrupt();
        verify(player, never()).stop();
        verify(player, never()).abort();
    }
}
