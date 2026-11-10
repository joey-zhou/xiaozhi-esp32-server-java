package com.xiaozhi.dialogue;

import com.xiaozhi.ai.llm.service.IntentService;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.audio.VadService;
import com.xiaozhi.dialogue.llm.factory.PersonaFactory;
import com.xiaozhi.dialogue.playback.FaultNotice;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.runtime.FaultNotifier;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.AudioUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户说了话却没能变成对话时，要分清「没说话」和「出了故障」：
 * 没说话就安静地丢掉；出了故障必须给用户一句话，否则设备一声不吭，用户只会以为设备坏了。
 */
@ExtendWith(MockitoExtension.class)
class DialogueServiceFaultNoticeTest {

    private static final String SESSION_ID = "fault-session";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(3);

    @Mock
    private VadService vadService;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private IntentService intentService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private StorageServiceFactory storageServiceFactory;
    @Mock
    private FaultNotifier faultNotifier;
    @Mock
    private PersonaFactory personaFactory;
    @Mock
    private Persona persona;
    @Mock
    private SttService sttService;
    @Mock
    private Player player;
    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;

    @InjectMocks
    private DialogueService dialogueService;

    @TempDir
    private Path tempDir;

    private WebSocketSession session;
    private String originalAudioPath;

    @BeforeEach
    void setUp() {
        originalAudioPath = AudioUtils.AUDIO_PATH;
        AudioUtils.AUDIO_PATH = tempDir.resolve("audio").toString() + "/";
        lenient().when(springSession.getId()).thenReturn(SESSION_ID);
        session = new WebSocketSession(springSession);
        session.setDevice(boundDevice());
        session.setMode(ListenMode.AUTO);
        session.setPersona(persona);
        session.setPlayer(player);
        lenient().when(persona.getSttService()).thenReturn(sttService);
        lenient().when(persona.getPlayer()).thenReturn(player);
        lenient().when(sttService.getProviderName()).thenReturn("fake-stt");
        lenient().when(vadService.getPcmData(SESSION_ID)).thenReturn(List.of(frame()));
    }

    @AfterEach
    void tearDown() {
        AudioUtils.AUDIO_PATH = originalAudioPath;
    }

    /** 服务商报错、重放后仍然失败：请用户再说一遍 */
    @Test
    void failedRecognitionAsksTheUserToRepeat() {
        runTurn(SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR));

        verify(faultNotifier, timeout(AWAIT_TIMEOUT.toMillis())).notice(session, FaultNotice.MISHEARD);
    }

    /** 接连失败说明不是偶然（比如密钥过期），再让用户重说只会越说越烦 */
    @Test
    void repeatedFailureTellsTheUserToComeBackLater() {
        session.recordSttFailure();

        runTurn(SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR));

        verify(faultNotifier, timeout(AWAIT_TIMEOUT.toMillis())).notice(session, FaultNotice.CANNOT_HEAR);
    }

    /** 识别成功一次，之前的失败就不算「接连」了 */
    @Test
    void successfulRecognitionResetsTheFailureStreak() {
        session.recordSttFailure();

        runTurn(SttResult.textOnly("你好"));

        verify(persona, timeout(AWAIT_TIMEOUT.toMillis())).chat(any(), anyBoolean(), anyLong());
        assertThat(session.recordSttFailure()).isEqualTo(1);
    }

    /** 用户本来就没说话（或只是噪声），安静地丢掉，不能对着空气道歉 */
    @Test
    void silenceIsDiscardedWithoutANotice() {
        runTurn(SttResult.textOnly(""));

        verify(faultNotifier, after(500).never()).notice(any(), any());
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.IDLE);
    }

    /** 识别服务没配好或本地模型没就位：每一句都会落到这里，是本地部署最常见的事故 */
    @Test
    void missingRecognitionServiceTellsTheUserItCannotHear() {
        when(persona.getSttService()).thenReturn(null);

        runTurn(null);

        verify(faultNotifier, timeout(AWAIT_TIMEOUT.toMillis())).notice(session, FaultNotice.CANNOT_HEAR);
    }

    /** 会话没建起来（比如没有可用的对话模型），说什么都不会有回应 */
    @Test
    void sessionWithoutAPersonaTellsTheUserItIsUnavailable() {
        session.setPersona(null);

        runTurn(null);

        verify(faultNotifier, timeout(AWAIT_TIMEOUT.toMillis())).notice(session, FaultNotice.UNAVAILABLE);
    }

    /** 设备正在说话时（用户插话、识别又失败），插一句道歉比继续原来的播放更糟 */
    @Test
    void noNoticeWhileTheDeviceIsSpeaking() {
        when(sttService.stream(any(), any(), any())).thenAnswer(invocation -> {
            invocation.<Flux<byte[]>>getArgument(0).blockLast(AWAIT_TIMEOUT);
            session.transitionTo(DeviceState.SPEAKING);
            return SttResult.failure(SttResult.FAILURE_LOCAL_ERROR);
        });

        speak();

        verify(sttService, timeout(AWAIT_TIMEOUT.toMillis()).atLeastOnce()).stream(any(), any(), any());
        verify(faultNotifier, after(500).never()).notice(any(), any());
    }

    /** 用户叫了设备，会话却建不起来：一声不吭只会让人以为设备坏了 */
    @Test
    void wakeWordWithoutAWorkingSessionTellsTheUserItIsUnavailable() {
        when(personaFactory.buildPersona(session)).thenThrow(new IllegalStateException("没有可用的对话模型"));

        dialogueService.handleWakeWord(session, "你好小智");

        verify(faultNotifier).notice(session, FaultNotice.UNAVAILABLE);
    }

    /** 起流 → 收句 → 每次识别都返回同一个结果（重放也一样）；result 为 null 表示不会走到识别 */
    private void runTurn(SttResult result) {
        if (result != null) {
            when(sttService.stream(any(), any(), any())).thenAnswer(invocation -> {
                invocation.<Flux<byte[]>>getArgument(0).collectList().block(AWAIT_TIMEOUT);
                return result;
            });
        }
        speak();
    }

    private void speak() {
        when(vadService.processAudio(eq(SESSION_ID), any(), anyLong()))
                .thenReturn(new VadService.VadResult(VadService.VadStatus.SPEECH_START, frame()),
                        new VadService.VadResult(VadService.VadStatus.SPEECH_END, frame()));

        dialogueService.processAudioData(session, frame());
        dialogueService.processAudioData(session, frame());
    }

    private static byte[] frame() {
        byte[] payload = new byte[60];
        ThreadLocalRandom.current().nextBytes(payload);
        return payload;
    }

    private static DeviceBO boundDevice() {
        DeviceBO device = new DeviceBO();
        device.setDeviceId("94:a9:90:2b:dd:18");
        device.setRoleId(1);
        device.setUserId(1);
        return device;
    }
}
