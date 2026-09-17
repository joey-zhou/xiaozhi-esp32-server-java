package com.xiaozhi.dialogue;

import com.xiaozhi.ai.llm.service.IntentService;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.audio.VadService;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.AudioUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 说到 STT 单段上限换识别流：中间段的终稿只拼接不回答，换流时把已识别文本先发给设备；
 * 最后一段收句后各段按顺序拼成一条用户消息，只回答一次。
 * 中间段识别失败时重放的是换流时交出的那段 PCM，不是新一段的缓冲。
 */
@ExtendWith(MockitoExtension.class)
class DialogueServiceSegmentRotationTest {

    private static final String SESSION_ID = "rotate-session";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(3);
    /** 帧首字节标记这帧属于哪段：识别假体据此决定返回哪份终稿，两段线程起跑先后不影响 */
    private static final byte FIRST_SEGMENT = 1;
    private static final byte SECOND_SEGMENT = 2;
    private static final byte REPLAY = 9;

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
    private final AtomicInteger sttCalls = new AtomicInteger();
    /** 每次 stream 收到的音频 */
    private final List<List<byte[]>> receivedAudio = new CopyOnWriteArrayList<>();

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
        lenient().when(vadService.getPcmData(SESSION_ID)).thenReturn(List.of(frame()));
    }

    @AfterEach
    void tearDown() {
        AudioUtils.AUDIO_PATH = originalAudioPath;
    }

    @Test
    void rotatedSegmentsAreMergedIntoOneUserMessage() {
        scriptStt(Map.of(
                FIRST_SEGMENT, SttResult.textOnly("我先说前半段，"),
                SECOND_SEGMENT, SttResult.textOnly("再说后半段。")));

        runRotatedTurn(List.of(frame(REPLAY), frame(REPLAY)));

        ArgumentCaptor<UserMessage> userMessage = ArgumentCaptor.forClass(UserMessage.class);
        verify(persona, timeout(AWAIT_TIMEOUT.toMillis()).times(1)).chat(userMessage.capture(), eq(true), anyLong());
        assertThat(sttCalls.get()).isEqualTo(2);
        // 换流时先发前半段，收句后发整句
        var order = inOrder(player);
        order.verify(player).sendStt("我先说前半段，");
        order.verify(player).sendStt("我先说前半段，再说后半段。");
        assertThat(userMessage.getValue().getText()).isEqualTo("我先说前半段，再说后半段。");
        verify(persona, times(1)).prepareTurn();
    }

    @Test
    void failedRotatedSegmentReplaysItsOwnPcm() {
        List<byte[]> firstSegmentPcm = List.of(frame(REPLAY), frame(REPLAY), frame(REPLAY));
        scriptStt(Map.of(
                FIRST_SEGMENT, SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR),
                REPLAY, SttResult.textOnly("前半段"),
                SECOND_SEGMENT, SttResult.textOnly("后半段")));

        runRotatedTurn(firstSegmentPcm);

        verify(player, timeout(AWAIT_TIMEOUT.toMillis())).sendStt("前半段后半段");
        // 第一段失败 → 重放第一段 → 第二段，共三次识别，重放收到的正是换流时交出的那段
        assertThat(sttCalls.get()).isEqualTo(3);
        List<byte[]> replayed = receivedAudio.stream()
                .filter(frames -> !frames.isEmpty() && frames.get(0)[0] == REPLAY)
                .findFirst().orElseThrow();
        assertThat(replayed).containsExactlyElementsOf(firstSegmentPcm);
    }

    /** 按这条流第一帧的标记返回终稿：段首帧是 VAD 交给该段的第一帧，重放流的首帧是交出的 PCM */
    private void scriptStt(Map<Byte, SttResult> resultsByMarker) {
        when(sttService.stream(any(), any())).thenAnswer(invocation -> {
            Flux<byte[]> audio = invocation.getArgument(0);
            sttCalls.incrementAndGet();
            List<byte[]> frames = audio.collectList().block(AWAIT_TIMEOUT);
            receivedAudio.add(frames);
            byte marker = frames != null && !frames.isEmpty() ? frames.get(0)[0] : 0;
            return resultsByMarker.getOrDefault(marker, SttResult.textOnly(""));
        });
    }

    /**
     * 驱动一次说话：SPEECH_START 起流 → SPEECH_ROTATE 换流并交出第一段 PCM → SPEECH_END 收句。
     * 第一段的识别流在换流时终结，其线程先于收句返回。本轮在后台线程收尾，断言用 Mockito 的等待窗口接住。
     */
    private void runRotatedTurn(List<byte[]> firstSegmentPcm) {
        when(vadService.processAudio(eq(SESSION_ID), any(), anyLong()))
                .thenReturn(new VadService.VadResult(VadService.VadStatus.SPEECH_START, frame(FIRST_SEGMENT)),
                        new VadService.VadResult(VadService.VadStatus.SPEECH_ROTATE, frame(SECOND_SEGMENT),
                                firstSegmentPcm),
                        new VadService.VadResult(VadService.VadStatus.SPEECH_END, frame(SECOND_SEGMENT)));

        dialogueService.processAudioData(session, frame());
        dialogueService.processAudioData(session, frame());
        dialogueService.processAudioData(session, frame());
    }

    private static byte[] frame() {
        return frame((byte) 0);
    }

    private static byte[] frame(byte marker) {
        byte[] payload = new byte[60];
        ThreadLocalRandom.current().nextBytes(payload);
        payload[0] = marker;
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
