package com.xiaozhi.communication.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;
import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.server.websocket.BinaryProtocolCodec;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.Synthesizer;
import com.xiaozhi.dialogue.runtime.DialogueTurn;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.dialogue.runtime.PersonaListener;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.event.ChatAbortedEvent;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.OpusProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 钉住「播放中被打断」这条链路的协议不变量：
 * <ul>
 *   <li>设备 abort 后下行音频必须立刻停住，tts stop 只发一次，AEC 参考队列必须清空，
 *       否则上一轮的残帧会串进下一轮播放、旧参考帧会污染回声对齐；</li>
 *   <li>用户开口先暂停不打断：暂停期间时间轴用静音帧续着，续播后真帧接着原序列走，
 *       不重不漏不乱序——这是"误打断续播"能被用户接受的前提；</li>
 *   <li>附和词与设备自身回声一律续播，不截历史、不起新一轮；</li>
 *   <li>真打断才截历史，且只截到用户实际听到的那几句；</li>
 *   <li>被新一轮顶掉的过期识别结果必须整条丢弃，不能补发一轮多余对话；</li>
 *   <li>没有播放器时的 abort 也必须回一条 tts stop，让设备切回聆听。</li>
 * </ul>
 *
 * <p>本组用例用预编码 Opus 帧直接喂真实 ScheduledPlayer 来构造播放现场（TTS 合成本身不在协议套件范围），
 * 打断则一律走真实协议链路：设备文本帧 → WebSocketHandler → MessageHandler → ChatAbortedEvent → DialogueService。
 */
class AbortAndResumeProtocolTest {

    private static final String DEVICE_ID = "94:a9:90:2b:dd:19";
    /** 一轮播放排入的真帧数，够长到打断/暂停都发生在播放中途 */
    private static final int PLAYBACK_FRAMES = 30;

    private ProtocolTestHarness harness;
    private Conversation conversation;
    private PersonaListener personaListener;
    private String originalAudioPath;

    @TempDir
    Path audioDir;

    @BeforeEach
    void setUp() {
        // AUDIO_PATH 由 RuntimePathConfig 在启动时注入，不起容器时为 null，
        // 而真打断后的新一轮会走到用户音频落盘，必须先给值
        originalAudioPath = AudioUtils.AUDIO_PATH;
        AudioUtils.AUDIO_PATH = audioDir.toString();
        harness = ProtocolTestHarness.create();
    }

    @AfterEach
    void tearDown() {
        harness.shutdown();
        AudioUtils.AUDIO_PATH = originalAudioPath;
    }

    @Test
    void abortDuringPlaybackStopsFramesAndSendsSingleTtsStop() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        ChatSession session = device.session();
        List<byte[]> frames = fakeOpusFrames(PLAYBACK_FRAMES);
        startPlayback(session, frames, Map.of(0, "今天先讲到这里，我们接着说下一个话题。"), true);

        // 播放中用户仍在录音，本轮音频流是开着的（等同 startStt 建流后的状态）
        session.createAudioStream();
        // 播到第几帧不确定，等下行真帧确实出去几帧再打断
        AwaitHelper.until("播放已开始下发真帧", () -> spokenFrames(device).size() >= 3);
        device.abort("wake_word_detected");

        // 打断原因原样透传，服务端不改写
        List<ChatAbortedEvent> aborted = harness.events().eventsOf(ChatAbortedEvent.class);
        assertThat(aborted).hasSize(1);
        assertThat(aborted.get(0).getReason()).isEqualTo("wake_word_detected");
        assertThat(aborted.get(0).getSessionId()).isEqualTo(device.sessionId());

        Player player = session.getPlayer();
        AwaitHelper.until("播放器已清空待播内容", () -> !player.hasContent());
        int stoppedAt = device.transport().binaryFrames().size();
        // 下行立刻停住：只容忍一帧在途，不容忍继续按节拍下发。
        // 队列没清干净的话，300ms 内会再冒出四五帧
        AwaitHelper.stayFalse("打断后仍在按节拍下发音频帧", Duration.ofMillis(300),
                () -> device.transport().binaryFrames().size() > stoppedAt + 1);
        // 队列里剩下的帧被丢弃，而不是继续播完
        assertThat(spokenFrames(device).size()).isLessThan(PLAYBACK_FRAMES);

        // tts stop 只发一次：abort 发一条，发送线程按代次退出不再补发
        assertThat(signatureCount(device, "tts:stop")).isEqualTo(1);
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.LISTENING);
        // 本轮音频流被终结并释放，下一轮从新流开始
        assertThat(session.getAudioSinks()).isNull();
        // 已发未播的帧会被设备丢弃，待喂入的参考帧必须清掉
        assertThat(harness.aec().clearReferenceCalls()).containsExactly(device.sessionId());
    }

    @Test
    void backchannelDuringPlaybackPausesThenResumesWithoutLosingFrames() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        ChatSession session = device.session();
        installPersona(device);
        List<byte[]> frames = fakeOpusFrames(PLAYBACK_FRAMES);
        startPlayback(session, frames, Map.of(0, "北京今天多云转晴，白天最高气温二十六度。"), true);
        AwaitHelper.until("播放已开始下发真帧", () -> spokenFrames(device).size() >= 2);

        // 用户在播放中插了句"嗯嗯"：ASR 首字先暂停，终稿判定为附和后续播
        harness.stt().withPartials("嗯").withFinalText("嗯嗯");
        device.listenStart(ListenMode.Auto);
        device.speak(ScriptedVadService.SPEECH_START);

        Player player = session.getPlayer();
        AwaitHelper.until("ASR 首字已暂停播放", player::isPaused);
        AwaitHelper.until("暂停期间按节拍下发静音帧", () -> silenceFrames(device).size() >= 2);
        int frozenSpoken = spokenFrames(device).size();
        AwaitHelper.until("静音帧持续下发", () -> silenceFrames(device).size() >= 6);
        // 暂停期间时间轴不断，但一帧真音频都不该再下发
        assertThat(spokenFrames(device)).hasSize(frozenSpoken);

        device.speak(ScriptedVadService.SPEECH_END);
        AwaitHelper.until("终稿判为附和后已续播", () -> !player.isPaused());
        AwaitHelper.until("续播后真帧继续下发", () -> spokenFrames(device).size() > frozenSpoken);
        // 附和不是打断：全程不发 tts stop，也不进新一轮对话
        assertThat(device.transport().jsonSignatures()).doesNotContain("tts:stop");
        assertThat(device.transport().jsonSignatures()).doesNotContain("stt");
        assertThat(conversation.rawMessages()).isEmpty();

        AwaitHelper.until("本轮真帧全部下发完毕", Duration.ofSeconds(10),
                () -> spokenFrames(device).size() == PLAYBACK_FRAMES);
        // 暂停前后拼起来正好是原始帧序列，不重不漏不乱序
        assertThat(hex(spokenFrames(device))).containsExactlyElementsOf(hex(frames));
    }

    @Test
    void realInterruptDuringPlaybackTruncatesHistoryToHeardSentences() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        ChatSession session = device.session();
        Persona persona = installPersona(device);

        // 建立一轮对话：用户消息已入历史，回复正在生成并已下发两句
        persona.chat(userMessage("讲个故事"), false);
        List<byte[]> frames = fakeOpusFrames(PLAYBACK_FRAMES);
        startPlayback(session, frames, Map.of(0, "从前有座山，", 5, "山里有座庙。"), true);
        AwaitHelper.until("两句回复都已开始下发",
                () -> signatureCount(device, "tts:sentence_start") == 2);

        // 用户说了句实义内容，既不是附和也不是回声
        harness.stt().withPartials("换").withFinalText("换一个");
        device.listenStart(ListenMode.Auto);
        device.speak(ScriptedVadService.SPEECH_START);
        AwaitHelper.until("ASR 首字已暂停播放", () -> session.getPlayer().isPaused());
        device.speak(ScriptedVadService.SPEECH_END);

        // 确认打断：发 tts stop，并按新一轮的识别结果继续
        JsonNode stt = device.transport().awaitJson("stt");
        assertThat(stt.path("text").asText()).isEqualTo("换一个");
        assertThat(signatureCount(device, "tts:stop")).isEqualTo(1);

        // 历史只留用户听到的那两句，且本轮被标记为打断
        ArgumentCaptor<DialogueTurn> captor = ArgumentCaptor.forClass(DialogueTurn.class);
        verify(personaListener).onDialogueTurn(captor.capture());
        DialogueTurn interrupted = captor.getValue();
        assertThat(interrupted.isInterrupted()).isTrue();
        assertThat(interrupted.getAssistantMessage().getText()).isEqualTo("从前有座山，山里有座庙。");

        AwaitHelper.until("新一轮用户消息已入历史", () -> conversation.rawMessages().size() == 3);
        List<Message> history = List.copyOf(conversation.rawMessages());
        assertThat(history.get(0)).isInstanceOf(UserMessage.class);
        assertThat(history.get(0).getText()).isEqualTo("讲个故事");
        assertThat(history.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(history.get(1).getText()).isEqualTo("从前有座山，山里有座庙。");
        assertThat(history.get(2)).isInstanceOf(UserMessage.class);
        assertThat(history.get(2).getText()).isEqualTo("换一个");
    }

    @Test
    void echoOfOwnSentenceResumesInsteadOfInterrupting() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        ChatSession session = device.session();
        Persona persona = installPersona(device);
        persona.chat(userMessage("今天天气怎么样"), false);

        String sentence = "今天天气不错，适合出门散步。";
        List<byte[]> frames = fakeOpusFrames(PLAYBACK_FRAMES);
        startPlayback(session, frames, Map.of(0, sentence), true);
        AwaitHelper.until("回复已开始下发", () -> signatureCount(device, "tts:sentence_start") == 1);

        // 设备把自己刚播的那句拾了回来，终稿与下发文本一致
        harness.stt().withPartials("今天天气").withFinalText(sentence);
        device.listenStart(ListenMode.Auto);
        device.speak(ScriptedVadService.SPEECH_START);

        Player player = session.getPlayer();
        AwaitHelper.until("ASR 首字已暂停播放", player::isPaused);
        // 等静音帧起来，确保在途的最后一帧真音频已落地，基线才稳
        AwaitHelper.until("暂停期间按节拍下发静音帧", () -> silenceFrames(device).size() >= 2);
        int frozenSpoken = spokenFrames(device).size();
        device.speak(ScriptedVadService.SPEECH_END);

        // 回声只续播，不打断
        AwaitHelper.until("识别为回声后已续播", () -> !player.isPaused());
        AwaitHelper.until("续播后真帧继续下发", () -> spokenFrames(device).size() > frozenSpoken);
        AwaitHelper.until("本轮识别已收尾", () -> harness.stt().completedStreams() == 1);

        assertThat(device.transport().jsonSignatures()).doesNotContain("tts:stop");
        // 回声不产生新一轮：不回 stt、不截历史、历史里只有原来那条用户消息
        assertThat(device.transport().jsonSignatures()).doesNotContain("stt");
        verify(personaListener, never()).onDialogueTurn(any());
        verify(personaListener, never()).onDialogueTurnTruncated(any(), any(), any());
        assertThat(conversation.rawMessages()).hasSize(1);
        assertThat(conversation.rawMessages().get(0).getText()).isEqualTo("今天天气怎么样");
        assertThat(harness.aec().clearReferenceCalls()).isEmpty();
    }

    @Test
    void staleSttResultAfterNewTurnIsDiscarded() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        installPersona(device);
        // 第一轮识别在音频流结束后一直挂着不返回
        harness.stt().hangUntilReleased().withFinalText("现在几点了");
        device.listenStart(ListenMode.Auto);

        device.speak(ScriptedVadService.SPEECH_START, ScriptedVadService.SPEECH_CONTINUE);
        device.speak(ScriptedVadService.SPEECH_END);
        AwaitHelper.until("第一轮音频已全部进识别", () -> harness.stt().receivedFrames().size() == 2);

        // 第二轮开口，startStt 同步换掉 audioSinks，第一轮就此作废
        device.speak(ScriptedVadService.SPEECH_START);
        assertThat(harness.stt().streamCalls()).isEqualTo(2);
        device.speak(ScriptedVadService.SPEECH_END);

        harness.stt().release();
        AwaitHelper.until("两轮识别都已返回终稿", () -> harness.stt().completedStreams() == 2);

        JsonNode stt = device.transport().awaitJson("stt");
        assertThat(stt.path("text").asText()).isEqualTo("现在几点了");
        AwaitHelper.until("新一轮用户消息已入历史", () -> conversation.rawMessages().size() == 1);
        // 过期轮次整条丢弃：不补发 stt，也不再起一轮对话
        AwaitHelper.stayFalse("过期识别结果又出了一条 stt", Duration.ofMillis(300),
                () -> signatureCount(device, "stt") > 1);
        assertThat(conversation.rawMessages()).hasSize(1);
        assertThat(conversation.rawMessages().get(0).getText()).isEqualTo("现在几点了");
    }

    @Test
    void abortBeforeAnyPlayerExistsStillNotifiesDevice() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        ChatSession session = device.session();

        // 一次播放都没发生过就 abort
        device.abort("wake_word_detected");
        assertThat(signatureCount(device, "tts:stop")).isEqualTo(1);
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.LISTENING);
        assertThat(device.transport().binaryFrames()).isEmpty();

        // 播放器与 Persona 都还没建起来（超时告别收尾后也是这个状态）时的 abort
        session.setPlayer(null);
        session.setPersona(null);
        session.transitionTo(DeviceState.IDLE);
        device.abort("wake_word_detected");

        assertThat(signatureCount(device, "tts:stop")).isEqualTo(2);
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.LISTENING);
        assertThat(device.transport().isOpen()).isTrue();
        assertThat(harness.events().eventsOf(ChatAbortedEvent.class)).hasSize(2);
    }

    // ========== 驱动与断言辅助 ==========

    /**
     * 装一个带真实 Conversation 的 Persona 顶掉脚手架的默认实现：
     * 打断收尾要落到历史上才看得见，而脚手架默认 Persona 没有 conversation / listener。
     * 合成器用 mock：不订阅 LLM 流，轮次停在准备阶段，正是"回复还在生成中就被打断"的现场。
     */
    private Persona installPersona(FakeDevice device) {
        ChatSession session = device.session();
        conversation = new Conversation(device.deviceId(), 1, session.getSessionId(), "协议测试角色", 1);
        personaListener = mock(PersonaListener.class);
        Persona persona = Persona.builder()
                .sessionManager(harness.sessionManager())
                .sessionId(session.getSessionId())
                .sttService(harness.stt())
                .player(session.getPlayer())
                .synthesizer(mock(Synthesizer.class))
                .conversation(conversation)
                .listener(personaListener)
                .build();
        session.setPersona(persona);
        return persona;
    }

    /** 带时间戳的用户消息，DialogueTurn 强制要求用户消息创建时间非空 */
    private static UserMessage userMessage(String text) {
        UserMessage message = UserMessage.builder().text(text).metadata(new HashMap<>()).build();
        MessageTimeMetadata.setTimeMillis(message, Instant.now());
        return message;
    }

    /**
     * 用预编码 Opus 帧驱动真实播放器。
     * sentences 的 key 是帧下标，该帧下发时随帧发出 sentence_start。
     *
     * @param reply 是否本轮 LLM 回复，只有回复句子计入打断截断
     */
    private static void startPlayback(ChatSession session, List<byte[]> frames,
                                      Map<Integer, String> sentences, boolean reply) {
        List<Speech> speeches = new ArrayList<>(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            String text = sentences.get(i);
            speeches.add(text != null ? Speech.ofOpus(frames.get(i), text) : Speech.ofOpus(frames.get(i)));
        }
        session.getPlayer().play(Flux.fromIterable(speeches), reply);
    }

    /** 每帧内容唯一，便于断言下行序列不重不漏不乱序；长度与内容都与静音帧不同 */
    private static List<byte[]> fakeOpusFrames(int count) {
        List<byte[]> frames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] frame = new byte[48];
            Arrays.fill(frame, (byte) 0x5A);
            frame[0] = (byte) 0xF0;
            frame[1] = (byte) (i >>> 8);
            frame[2] = (byte) i;
            frames.add(frame);
        }
        return frames;
    }

    /** 下行的真音频帧，剔除按节拍补的静音帧 */
    private static List<byte[]> spokenFrames(FakeDevice device) {
        byte[] silence = OpusProcessor.silenceFrame();
        return device.transport().binaryPayloads(BinaryProtocolCodec.VERSION_V1).stream()
                .filter(payload -> !Arrays.equals(payload, silence))
                .toList();
    }

    /** 下行的静音帧，暂停、句间、上游断流时按节拍下发 */
    private static List<byte[]> silenceFrames(FakeDevice device) {
        byte[] silence = OpusProcessor.silenceFrame();
        return device.transport().binaryPayloads(BinaryProtocolCodec.VERSION_V1).stream()
                .filter(payload -> Arrays.equals(payload, silence))
                .toList();
    }

    /** byte[] 的 equals 是引用比较，序列断言统一转成十六进制字符串 */
    private static List<String> hex(List<byte[]> frames) {
        return frames.stream().map(frame -> HexFormat.of().formatHex(frame)).toList();
    }

    private static long signatureCount(FakeDevice device, String signature) {
        return device.transport().jsonSignatures().stream().filter(signature::equals).count();
    }
}
