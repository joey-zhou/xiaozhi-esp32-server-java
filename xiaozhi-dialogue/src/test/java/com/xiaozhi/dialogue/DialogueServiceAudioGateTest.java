package com.xiaozhi.dialogue;

import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.audio.VadService;
import com.xiaozhi.dialogue.audio.VadService.VadResult;
import com.xiaozhi.dialogue.audio.VadService.VadStatus;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.enums.DeviceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住音频入口的前置门禁与本轮归属判定。
 *
 * <p>三条不变量：告别语等收尾回调播放期间、设备未绑角色时一律不进 VAD，否则会话关不掉；
 * VAD 尚未初始化时的音频只进唤醒词前置缓冲，送进识别会被当成一句话多起一轮对话；
 * STT 终稿回来时音频流已被新一轮换掉的，过期文本不得再触发对话，异常路径必须把暂停的播放放回去，
 * 否则要等 5 秒兜底才续播，体感是机器人卡住。
 */
@ExtendWith(MockitoExtension.class)
class DialogueServiceAudioGateTest {

    private static final String SESSION_ID = "gate-session";

    @Mock
    private VadService vadService;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private Player player;
    @Mock
    private Persona persona;
    @Mock
    private SttService sttService;
    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;

    @InjectMocks
    private DialogueService dialogueService;

    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        lenient().when(springSession.getId()).thenReturn(SESSION_ID);
        session = new WebSocketSession(springSession);
        session.setDevice(boundDevice());
        session.setPlayer(player);
    }

    @Test
    void audioIsIgnoredWhilePlayerHasPendingFunction() {
        // 告别语播放中，再收音会让会话关不掉
        when(player.getFunctionAfterChat()).thenReturn(() -> {
        });

        dialogueService.processAudioData(session, opusFrame());

        verify(vadService, never()).processAudio(any(), any(), anyLong());
        assertThat(session.drainWakeWordAudio()).isEmpty();
    }

    @Test
    void audioIsIgnoredBeforeDeviceIsBound() {
        session.setDevice(null);

        dialogueService.processAudioData(session, opusFrame());

        verify(vadService, never()).processAudio(any(), any(), anyLong());
        assertThat(session.drainWakeWordAudio()).isEmpty();
    }

    @Test
    void audioIsIgnoredWhenDeviceHasNoRole() {
        DeviceBO device = boundDevice();
        device.setRoleId(null);
        session.setDevice(device);

        dialogueService.processAudioData(session, opusFrame());

        verify(vadService, never()).processAudio(any(), any(), anyLong());
    }

    @Test
    void preListenAudioIsCapturedAsWakeWord() {
        // VAD 未初始化：设备在 listen/start 之前补发的唤醒词音频，只采集不送识别
        when(vadService.processAudio(eq(SESSION_ID), any(), anyLong())).thenReturn(null);
        byte[] frame = opusFrame();

        dialogueService.processAudioData(session, frame);

        assertThat(session.drainWakeWordAudio()).containsExactly(frame);
        verify(sessionManager, never()).updateLastActivity(SESSION_ID);
    }

    @Test
    void continueFeedsAudioStreamWhileListening() {
        byte[] processed = {9, 8, 7};
        when(vadService.processAudio(eq(SESSION_ID), any(), anyLong()))
                .thenReturn(new VadResult(VadStatus.SPEECH_CONTINUE, processed));
        session.transitionTo(DeviceState.LISTENING);
        List<byte[]> received = subscribeAudioStream();

        dialogueService.processAudioData(session, opusFrame());

        assertThat(received).containsExactly(processed);
    }

    @Test
    void continueDoesNotFeedAudioStreamWhenNotListening() {
        when(vadService.processAudio(eq(SESSION_ID), any(), anyLong()))
                .thenReturn(new VadResult(VadStatus.SPEECH_CONTINUE, new byte[] {9, 8, 7}));
        // 上一轮 TTS 还在播，本轮音频不能串进识别流
        session.transitionTo(DeviceState.SPEAKING);
        List<byte[]> received = subscribeAudioStream();

        dialogueService.processAudioData(session, opusFrame());

        assertThat(received).isEmpty();
    }

    @Test
    void supersededTurnDoesNotStartNewDialogue() {
        when(vadService.processAudio(eq(SESSION_ID), any(), anyLong()))
                .thenReturn(new VadResult(VadStatus.SPEECH_START, new byte[] {1, 1}));
        session.setPersona(persona);
        when(persona.getSttService()).thenReturn(sttService);
        when(sttService.stream(any(), any())).thenAnswer(invocation -> {
            // 终稿回来之前新一轮已经换掉音频流，本轮结果作废
            session.createAudioStream();
            return SttResult.textOnly("过期的一句话");
        });

        dialogueService.processAudioData(session, opusFrame());

        // 作废分支没有可观测的收尾动作，用一个等待窗口确认这段时间里确实没往下走
        verify(persona, after(500).never()).prepareTurn();
        verify(persona, never()).getPlayer();
    }

    @Test
    void sttFailureResumesPausedPlayer() {
        when(vadService.processAudio(eq(SESSION_ID), any(), anyLong()))
                .thenReturn(new VadResult(VadStatus.SPEECH_START, new byte[] {1, 1}));
        session.setPersona(persona);
        when(persona.getSttService()).thenReturn(sttService);
        when(sttService.stream(any(), any())).thenThrow(new IllegalStateException("识别连接断开"));
        when(player.isPaused()).thenReturn(true);

        dialogueService.processAudioData(session, opusFrame());

        verify(player, timeout(2000)).resume();
    }

    private List<byte[]> subscribeAudioStream() {
        session.createAudioStream();
        List<byte[]> received = new ArrayList<>();
        session.getAudioSinks().asFlux().subscribe(received::add);
        return received;
    }

    private static DeviceBO boundDevice() {
        DeviceBO device = new DeviceBO();
        device.setDeviceId("aa:bb:cc:11:22:33");
        device.setRoleId(7);
        return device;
    }

    private static byte[] opusFrame() {
        return new byte[] {1, 2, 3, 4};
    }
}
