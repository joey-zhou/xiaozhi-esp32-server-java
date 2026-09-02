package com.xiaozhi.communication.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.common.Speech;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.playback.OpusRecorder;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.ScheduledPlayer;
import com.xiaozhi.dialogue.playback.Synthesizer;
import com.xiaozhi.dialogue.runtime.DialogueTurn;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.dialogue.runtime.PersonaListener;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.utils.AudioUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * 钉住 listen 状态机的协议不变量：四个 state 分支各自的收句语义必须泾渭分明。
 *
 * <p>具体是四条不变量：
 * <ul>
 *   <li>manual 由设备松手断句：有语音时 listen/stop 只完成音频流（STT 还要接着读本轮 pcm），
 *       不能关流也不能重置 VAD；无语音时才是取消聆听，关流回 IDLE；</li>
 *   <li>auto 与 realtime 一律由服务端 VAD 的 SPEECH_END 收句，设备不发 listen/stop 也能跑完一轮，
 *       且两种模式走的是同一条自动断句路径；</li>
 *   <li>listen/start 之前补发的音频只进唤醒词前置缓冲、不进识别，listen/detect 时被清空，
 *       缓冲有帧数上限，超量流量丢弃而不是撑爆会话；</li>
 *   <li>listen/text 先打断当前播放再下发新一轮，旧轮音频不能串到新一轮里。</li>
 * </ul>
 *
 * <p>LLM 与真实 TTS 不在协议套件范围内：本组用例用一个固定回一段带标记 opus 帧的合成器替身，
 * 让 Persona → Synthesizer → Player 这段真实链路能跑完，断言只落在协议出站消息与帧内容上。
 */
class ListenStateMachineTest {

    private static final String DEVICE_ID = "aa:bb:cc:11:22:33";
    private static final String SECOND_DEVICE_ID = "aa:bb:cc:11:22:44";

    /** 合成器替身产出的假 opus 帧前两字节固定为魔数，第三字节是轮次标记，用来区分新旧轮下行帧 */
    private static final byte FRAME_MAGIC_0 = 0x7A;
    private static final byte FRAME_MAGIC_1 = 0x7B;
    private static final byte ROUND_ONE = 1;
    private static final byte ROUND_TWO = 2;
    private static final byte ROUND_ANNOUNCEMENT = 9;

    private static final String REPLY_ONE = "第一段回复";
    private static final String REPLY_TWO = "第二段回复";

    @TempDir
    static Path audioDir;

    private static String originalAudioPath;

    private ProtocolTestHarness harness;
    /** Persona 打断收尾时提交的轮次，用来观察 onInterrupted 是否被调到 */
    private final List<DialogueTurn> committedTurns = new CopyOnWriteArrayList<>();

    private volatile String scriptedReply = REPLY_ONE;
    private volatile byte scriptedRound = ROUND_ONE;
    private volatile int scriptedFrameCount = 6;

    @BeforeAll
    static void redirectAudioDir() {
        // STT 出结果后会把本轮音频落盘，AUDIO_PATH 由启动配置注入，测试里必须自己指过去
        originalAudioPath = AudioUtils.AUDIO_PATH;
        AudioUtils.AUDIO_PATH = audioDir.toString();
    }

    @AfterAll
    static void restoreAudioDir() {
        AudioUtils.AUDIO_PATH = originalAudioPath;
    }

    @BeforeEach
    void setUp() {
        harness = ProtocolTestHarness.create();
        installPlayablePersona();
    }

    @AfterEach
    void tearDown() {
        harness.shutdown();
    }

    @Test
    void manualStopWithSpeechCompletesSegmentWithoutClosingStream() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();

        // manual：客户端松手断句，服务端不做自动收句
        device.listenStart(ListenMode.Manual);
        assertThat(harness.vad().autoSegmentOf(device.sessionId())).isFalse();

        device.speak(ScriptedVadService.SPEECH_START, ScriptedVadService.SPEECH_CONTINUE);
        Sinks.Many<byte[]> turnSink = device.session().getAudioSinks();
        assertThat(turnSink).isNotNull();

        device.listenStop();

        // 收句只 completeAudioStream：订阅者收到结束信号，但流引用必须还在，
        // 否则 STT 虚拟线程之后取不到本轮 pcm
        AwaitHelper.until("本轮识别收到音频流结束信号", () -> harness.stt().completedStreams() == 1);
        assertThat(device.session().getAudioSinks()).isSameAs(turnSink);
        assertThat(harness.stt().receivedFrames()).hasSize(2);
        assertThat(device.session().getDeviceState()).isEqualTo(DeviceState.THINKING);
        // 松手收句不重置 VAD 会话
        assertThat(harness.vad().isSessionInitialized(device.sessionId())).isTrue();
    }

    @Test
    void manualStopWithoutSpeechCancelsListening() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();

        // 先跑一轮完整的松手收句，让 audioSinks 留在非空状态：
        // 生产代码正是靠 VAD 本轮状态而不是 audioSinks 判断该走哪个分支
        device.listenStart(ListenMode.Manual);
        device.speak(ScriptedVadService.SPEECH_START, ScriptedVadService.SPEECH_CONTINUE);
        device.listenStop();
        AwaitHelper.until("上一轮识别已结束", () -> harness.stt().completedStreams() == 1);
        assertThat(device.session().getAudioSinks()).isNotNull();

        // 第二轮：开了监听但一帧语音都没有，listen/stop 应当是取消而不是收句
        device.listenStart(ListenMode.Manual);
        device.listenStop();

        // Stop 分支整段跑在投递线程上，listenStop() 返回时已经执行完，无需等待
        assertThat(device.session().getAudioSinks()).isNull();
        assertThat(device.session().getDeviceState()).isEqualTo(DeviceState.IDLE);
        assertThat(harness.vad().isSessionInitialized(device.sessionId())).isFalse();
        // 取消聆听刻意不重置 AEC，保留已收敛的滤波器给后续对话复用
        assertThat(harness.aec().resetCalls()).isEmpty();
        // 没有第二次识别
        assertThat(harness.stt().streamCalls()).isEqualTo(1);
    }

    @Test
    void autoModeSegmentsOnVadSpeechEndWithoutListenStop() {
        harness.stt().withFinalText("今天天气怎么样").hangUntilReleased();
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        device.transport().clearOutbound();

        device.listenStart(ListenMode.Auto);
        assertThat(harness.vad().autoSegmentOf(device.sessionId())).isTrue();

        device.speak(ScriptedVadService.SPEECH_START,
                ScriptedVadService.SPEECH_CONTINUE,
                ScriptedVadService.SPEECH_CONTINUE,
                ScriptedVadService.SPEECH_CONTINUE);
        // 设备全程不发 listen/stop，收句只能来自 VAD 的 SPEECH_END
        device.sendFrames(ScriptedVadService.SPEECH_END);

        // STT 终稿被闸住，此刻状态稳定停在 THINKING
        AwaitHelper.until("收句后进入 THINKING",
                () -> device.session().getDeviceState() == DeviceState.THINKING);
        // START + 3 个 CONTINUE 进流，SPEECH_END 那帧只用于收句
        AwaitHelper.until("本轮音频帧已全部进入识别流",
                () -> harness.stt().receivedFrames().size() >= 4);
        assertThat(harness.stt().receivedFrames()).hasSize(4);

        harness.stt().release();

        device.transport().awaitJson("tts:stop");
        assertThat(harness.stt().completedStreams()).isEqualTo(1);
        assertThat(device.transport().jsonSignatures())
                .containsSubsequence("stt", "tts:start", "llm", "tts:sentence_start", "tts:stop");
        assertThat(device.transport().awaitJson("stt").path("text").asText()).isEqualTo("今天天气怎么样");
        assertThat(sentenceStartTexts(device.transport())).containsExactly(REPLY_ONE);
        // 回复音频确实下发了，且都夹在 sentence_start 与 tts stop 之间
        List<byte[]> replyFrames = device.transport().binaryPayloads(1).stream()
                .filter(payload -> isTaggedFrame(payload, ROUND_ONE))
                .toList();
        assertThat(replyFrames).isNotEmpty();
    }

    @Test
    void realtimeModeUsesAutoSegmentationSameAsAuto() {
        harness.stt().withFinalText("再说一遍");

        List<String> autoSignatures = runOneVoiceTurn(harness.connect(DEVICE_ID), ListenMode.Auto);
        List<String> realtimeSignatures = runOneVoiceTurn(harness.connect(SECOND_DEVICE_ID), ListenMode.RealTime);

        // realtime 不等于 manual，同样由服务端自动断句
        assertThat(harness.vad().autoSegmentOf(harness.sessionManager()
                .getSessionByDeviceId(SECOND_DEVICE_ID).getSessionId())).isTrue();
        assertThat(harness.sessionManager().getSessionByDeviceId(SECOND_DEVICE_ID).getMode())
                .isEqualTo(ListenMode.RealTime);
        assertThat(realtimeSignatures).isEqualTo(autoSignatures);
        assertThat(realtimeSignatures)
                .containsSubsequence("stt", "tts:start", "llm", "tts:sentence_start", "tts:stop");
    }

    @Test
    void detectDrainsWakeWordPrebufferAndEntersSpeaking() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        device.transport().clearOutbound();

        // listen/start 之前补发的唤醒词音频：VAD 未初始化，只采集不送识别
        List<byte[]> prebuffered = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            byte[] payload = markedFrame(i);
            prebuffered.add(payload);
            device.sendAudio(payload);
        }
        assertThat(harness.vad().processedFrames()).isEmpty();
        assertThat(harness.stt().streamCalls()).isZero();

        ChatSession session = device.session();
        // 缓冲只能取一次，先取出验证内容，再放回去交给 detect 清空
        List<byte[]> buffered = session.drainWakeWordAudio();
        assertFramesEqual(buffered, prebuffered);
        buffered.forEach(session::addWakeWordAudio);

        device.listenDetect("小智");

        // 唤醒响应期间忽略 VAD，状态先切 SPEAKING
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.SPEAKING);
        assertThat(session.drainWakeWordAudio()).isEmpty();

        // 唤醒词与文本输入走同一条对话链路，回复经合成器替身下发
        JsonNode sentenceStart = device.transport().awaitJson("tts:sentence_start");
        assertThat(sentenceStart.path("text").asText()).isEqualTo(REPLY_ONE);
        // 唤醒响应期间不送识别
        assertThat(harness.stt().receivedFrames()).isEmpty();
    }

    @Test
    void wakeWordPrebufferIsCappedAt100Frames() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();

        List<byte[]> sent = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            byte[] payload = markedFrame(i);
            sent.add(payload);
            device.sendAudio(payload);
        }

        // 上限 100 帧，超量流量直接丢弃，留下的是最早的那批
        List<byte[]> buffered = device.session().drainWakeWordAudio();
        assertThat(buffered).hasSize(100);
        assertFramesEqual(buffered, sent.subList(0, 100));
        assertThat(device.transport().isOpen()).isTrue();
        assertThat(harness.sessionManager().getSession(device.sessionId())).isNotNull();
    }

    @Test
    void listenTextAbortsCurrentPlaybackBeforeSendingStt() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        device.transport().clearOutbound();

        // 第一轮：放一段够长的回复，确保第二条 listen/text 到达时仍在播
        scriptedReply = REPLY_ONE;
        scriptedRound = ROUND_ONE;
        scriptedFrameCount = 40;
        device.listenText("讲个故事");
        AwaitHelper.until("第一轮回复已开始下发",
                () -> sentenceStartTexts(device.transport()).contains(REPLY_ONE));
        AwaitHelper.until("第一轮音频帧已下发", () -> device.transport().binaryPayloads(1).stream()
                .anyMatch(payload -> isTaggedFrame(payload, ROUND_ONE)));

        device.transport().clearOutbound();
        scriptedReply = REPLY_TWO;
        scriptedRound = ROUND_TWO;
        scriptedFrameCount = 6;
        device.listenText("讲个笑话");

        AwaitHelper.until("第二轮回复已开始下发",
                () -> sentenceStartTexts(device.transport()).contains(REPLY_TWO));
        AwaitHelper.until("第二轮音频帧已下发", () -> device.transport().binaryPayloads(1).stream()
                .anyMatch(payload -> isTaggedFrame(payload, ROUND_TWO)));

        // 顺序：先打断出 tts stop，再下发新一轮的 stt 与回复
        assertThat(device.transport().jsonSignatures())
                .containsSubsequence("tts:stop", "stt", "llm", "tts:sentence_start");
        assertThat(device.transport().awaitJson("stt").path("text").asText()).isEqualTo("讲个笑话");

        List<FakeWebSocketTransport.Sent> outbound = device.transport().snapshot();
        int stopIndex = indexOfTtsStop(outbound);
        assertThat(stopIndex).isNotNegative();
        // 旧轮已入队的帧被清空，tts stop 之后一帧都不该再出现
        assertThat(taggedFramesAfter(outbound, stopIndex, ROUND_ONE)).isEmpty();
        assertThat(taggedFramesAfter(outbound, stopIndex, ROUND_TWO)).isNotEmpty();

        // 打断收尾把第一轮作为「被打断」轮次提交，助手消息截到用户听到的那句
        assertThat(committedTurns).hasSize(1);
        assertThat(committedTurns.get(0).isInterrupted()).isTrue();
        assertThat(committedTurns.get(0).getAssistantMessage().getText()).isEqualTo(REPLY_ONE);
    }

    // ========== 驱动与断言辅助 ==========

    /** 跑一轮「listen/start → 语音 → VAD 收句 → 回复播完」，返回本轮的出站消息签名 */
    private List<String> runOneVoiceTurn(FakeDevice device, ListenMode mode) {
        device.hello();
        device.transport().clearOutbound();
        device.listenStart(mode);
        device.speak(ScriptedVadService.SPEECH_START, ScriptedVadService.SPEECH_CONTINUE);
        device.sendFrames(ScriptedVadService.SPEECH_END);
        device.transport().awaitJson("tts:stop");
        return device.transport().jsonSignatures();
    }

    /** 把 personaFactory 换成能真正播出音频的装配，LLM 之外的链路全部走生产代码 */
    private void installPlayablePersona() {
        // 装配器里已经 stub 过，必须走 doAnswer 覆盖：when(mock.call()) 会先执行旧 answer
        lenient().doAnswer(invocation -> personaOf(invocation.getArgument(0)))
                .when(harness.personaFactory()).buildPersona(any(ChatSession.class));
        lenient().doAnswer(invocation -> personaOf(invocation.getArgument(0)))
                .when(harness.personaFactory()).buildPersona(any(ChatSession.class), any(), any());
    }

    private Persona personaOf(ChatSession session) {
        if (session.getPersona() != null) {
            return session.getPersona();
        }
        Player player = session.getPlayer();
        if (player == null) {
            player = new ScheduledPlayer(session, harness.messageSender());
            player.setOpusRecorder(new OpusRecorder(session, mock(MessageService.class),
                    harness.aec(), harness.storageServiceFactory()));
            session.setPlayer(player);
        }
        DeviceBO device = session.getDevice();
        Persona persona = Persona.builder()
                .sessionManager(harness.sessionManager())
                .sessionId(session.getSessionId())
                .sttService(harness.stt())
                .player(player)
                .synthesizer(scriptedSynthesizer(session, player))
                .conversation(new Conversation(device.getDeviceId(), device.getRoleId(),
                        session.getSessionId(), "协议测试角色", device.getUserId()))
                .listener(recordingListener())
                .build();
        session.setPersona(persona);
        return persona;
    }

    /**
     * 合成器替身：不订阅上游文本流（LLM 不在本套件范围），直接把预置的一段带标记 opus 帧
     * 交给真实 Player，走完 tts start / sentence_start / 二进制帧 / tts stop 的真实下发流程。
     */
    private Synthesizer scriptedSynthesizer(ChatSession session, Player player) {
        return new Synthesizer(session, (TtsService) null, player) {
            private final AtomicBoolean active = new AtomicBoolean();

            @Override
            public void synthesize(Flux<String> stringFlux) {
                active.set(true);
                try {
                    player.play(replyFlux(scriptedReply, scriptedRound, scriptedFrameCount), true);
                } finally {
                    active.set(false);
                }
            }

            @Override
            public void synthesize(String text) {
                active.set(true);
                try {
                    // 告别语等播报走非回复通道，reply=false
                    player.play(replyFlux(text, ROUND_ANNOUNCEMENT, 6));
                } finally {
                    active.set(false);
                }
            }

            @Override
            public void cancel() {
                active.set(false);
            }

            @Override
            public boolean isActive() {
                return active.get();
            }
        };
    }

    private PersonaListener recordingListener() {
        return new PersonaListener() {
            @Override
            public void onDialogueTurn(DialogueTurn turn) {
                committedTurns.add(turn);
            }

            @Override
            public void onDialogueTurnTruncated(Conversation conversation, Instant assistantMessageCreatedAt,
                                                String spokenText) {
            }

            @Override
            public void onError(Throwable error) {
            }
        };
    }

    /** 一句回复的音频：首帧带文本（对应一次 sentence_start），其余帧只有音频 */
    private static Flux<Speech> replyFlux(String text, byte round, int frameCount) {
        List<Speech> speeches = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            byte[] frame = new byte[60];
            frame[0] = FRAME_MAGIC_0;
            frame[1] = FRAME_MAGIC_1;
            frame[2] = round;
            frame[3] = (byte) i;
            speeches.add(i == 0 ? Speech.ofOpus(frame, text) : Speech.ofOpus(frame));
        }
        return Flux.fromIterable(speeches);
    }

    /** 上行帧带序号，用来断言唤醒词缓冲留下的是最早的那批 */
    private static byte[] markedFrame(int index) {
        byte[] payload = new byte[60];
        payload[0] = ScriptedVadService.NO_SPEECH;
        payload[1] = (byte) (index & 0xFF);
        payload[2] = (byte) ((index >> 8) & 0xFF);
        return payload;
    }

    private static boolean isTaggedFrame(byte[] payload, byte round) {
        return payload.length > 3
                && payload[0] == FRAME_MAGIC_0
                && payload[1] == FRAME_MAGIC_1
                && payload[2] == round;
    }

    private static List<byte[]> taggedFramesAfter(List<FakeWebSocketTransport.Sent> outbound, int fromIndex,
                                                  byte round) {
        List<byte[]> frames = new ArrayList<>();
        for (int i = fromIndex + 1; i < outbound.size(); i++) {
            FakeWebSocketTransport.Sent sent = outbound.get(i);
            if (sent.binary() && isTaggedFrame(sent.payload(), round)) {
                frames.add(sent.payload());
            }
        }
        return frames;
    }

    private static int indexOfTtsStop(List<FakeWebSocketTransport.Sent> outbound) {
        for (int i = 0; i < outbound.size(); i++) {
            FakeWebSocketTransport.Sent sent = outbound.get(i);
            if (!sent.binary() && sent.text().contains("\"type\":\"tts\"")
                    && sent.text().contains("\"state\":\"stop\"")) {
                return i;
            }
        }
        return -1;
    }

    /** byte[] 的 equals 是引用比较，逐帧按内容比 */
    private static void assertFramesEqual(List<byte[]> actual, List<byte[]> expected) {
        assertThat(actual).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.get(i)).containsExactly(expected.get(i));
        }
    }

    private static List<String> sentenceStartTexts(FakeWebSocketTransport transport) {
        return transport.jsonMessages().stream()
                .filter(node -> "tts".equals(node.path("type").asText())
                        && "sentence_start".equals(node.path("state").asText()))
                .map(node -> node.path("text").asText())
                .toList();
    }
}
