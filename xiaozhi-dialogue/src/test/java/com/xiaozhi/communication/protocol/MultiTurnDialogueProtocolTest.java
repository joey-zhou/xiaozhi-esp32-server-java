package com.xiaozhi.communication.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.ScheduledPlayer;
import com.xiaozhi.dialogue.playback.Synthesizer;
import com.xiaozhi.dialogue.runtime.GoodbyeMessageSupplier;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.dialogue.runtime.PersonaListener;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.event.TtsPlaybackCompletedEvent;
import com.xiaozhi.utils.AudioUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多轮对话的协议不变量：一轮的收尾必须把音频流、播放器状态与对话历史都交接干净，下一轮才从零开始。
 *
 * <p>这组用例钉的是「轮次边界」：每轮出站消息的种类与先后固定为 stt → tts start → 情绪 →
 * sentence_start → 音频帧 → tts stop；上一轮的识别流在下一轮开流前必须已终结且无订阅者；
 * 收句之后到达的尾音不得追加进上一轮的识别流；一轮打断不得回头改写更早那一轮的历史；
 * 退出意图不经 LLM，告别语必须播完才关会话，播放期间的 listen 一律忽略。
 *
 * <p>本类不测 VAD 断句质量、TTS 合成与 LLM 本身：VAD 走帧脚本、TTS 用预编码假帧、LLM 是 mock，
 * 播放调度只断言「有/无」「顺序」「不重不漏」，不断言精确帧数与时刻。
 */
class MultiTurnDialogueProtocolTest {

    private static final String DEVICE_ID = "94:a9:90:2b:dd:18";
    private static final String GOODBYE_TEXT = "好的，拜拜~有需要随时叫我哦！";
    /** 一句话下发几帧假 opus，只影响播放时长，不参与断言 */
    private static final int FRAMES_PER_SENTENCE = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path audioDir;

    private String originalAudioPath;
    private ProtocolTestHarness harness;
    private FakeDevice device;
    private ChatSession session;
    private Conversation conversation;
    private GatedSynthesizer synthesizer;
    private ChatModel chatModel;
    private PersonaListener personaListener;
    private final Deque<String> scriptedReplies = new ConcurrentLinkedDeque<>();

    @BeforeEach
    void setUp() {
        // 用户音频落盘走 AudioUtils.AUDIO_PATH，未设置时 Path.of(null,...) 会让整轮对话在 STT 线程里静默失败
        originalAudioPath = AudioUtils.AUDIO_PATH;
        AudioUtils.AUDIO_PATH = audioDir.toString();

        harness = ProtocolTestHarness.create();
        chatModel = mock(ChatModel.class);
        personaListener = mock(PersonaListener.class);
        when(chatModel.stream(any(Prompt.class)))
                .thenAnswer(invocation -> Flux.just(chatResponse(scriptedReplies.poll())));
        // 换掉脚手架的默认 Persona：这里要真实播放器 + 可编排的 LLM/TTS/对话历史
        doAnswer(invocation -> buildPersona(invocation.getArgument(0)))
                .when(harness.personaFactory()).buildPersona(any(ChatSession.class));
        doAnswer(invocation -> buildPersona(invocation.getArgument(0)))
                .when(harness.personaFactory()).buildPersona(any(ChatSession.class), any(), any());

        device = harness.connect(DEVICE_ID);
        device.hello();
        session = device.session();
    }

    @AfterEach
    void tearDown() {
        if (synthesizer != null) {
            synthesizer.releasePlayback();
        }
        harness.shutdown();
        AudioUtils.AUDIO_PATH = originalAudioPath;
    }

    @Test
    void threeConsecutiveTurnsKeepMessageOrderAndCleanStreams() {
        String[][] script = {
                {"今天天气怎么样", "外面是晴天。"},
                {"帮我放首歌", "好的，马上安排。"},
                {"现在几点了", "刚过八点。"}};
        List<Sinks.Many<byte[]>> turnSinks = new ArrayList<>();

        for (String[] turn : script) {
            runTurn(turn[0], turn[1]);

            // 每轮的出站流水都必须是完整的一轮，且 tts stop 之后不再有任何帧
            List<String> flow = outboundFlow();
            assertThat(flow).containsSubsequence("stt", "tts:start", "llm", "tts:sentence_start", "binary", "tts:stop");
            assertThat(Collections.frequency(flow, "tts:stop")).isEqualTo(1);
            assertThat(flow.get(flow.size() - 1)).isEqualTo("tts:stop");
            assertThat(device.transport().awaitJson("stt").path("text").asText()).isEqualTo(turn[0]);
            assertThat(device.transport().awaitJson("tts:sentence_start").path("text").asText()).isEqualTo(turn[1]);
            // 本轮开始时清过，所以只留本轮这一句
            assertThat(session.getPlayer().spokenSentences()).containsExactly(turn[1]);

            turnSinks.add(session.getAudioSinks());
        }

        // 每轮一条独立的音频流，且都已终结、无订阅者残留
        assertThat(turnSinks).doesNotHaveDuplicates();
        for (Sinks.Many<byte[]> sink : turnSinks) {
            AwaitHelper.until("本轮识别流的订阅已释放", () -> sink.currentSubscriberCount() == 0);
        }
        assertThat(harness.stt().streamCalls()).isEqualTo(script.length);
        assertThat(harness.stt().completedStreams()).isEqualTo(script.length);

        // 三轮历史严格 user/assistant 交替，不多不少
        List<Message> history = conversation.rawMessages();
        assertThat(types(history)).containsExactly(MessageType.USER, MessageType.ASSISTANT,
                MessageType.USER, MessageType.ASSISTANT, MessageType.USER, MessageType.ASSISTANT);
        assertThat(texts(history)).containsExactly("今天天气怎么样", "外面是晴天。",
                "帮我放首歌", "好的，马上安排。", "现在几点了", "刚过八点。");
    }

    @Test
    void exitIntentSendsGoodbyeAndClosesAfterPlayback() {
        // 告别语播到一半挂住，制造一段"正在播告别语"的窗口
        synthesizer.holdPlayback();
        harness.stt().withFinalText("再见");
        device.transport().clearOutbound();

        device.listenStart(ListenMode.Auto);
        speakOneSegment();

        assertThat(device.transport().awaitJson("tts:sentence_start").path("text").asText()).isEqualTo(GOODBYE_TEXT);
        // 退出意图走关键词快路径，一个字都不进 LLM
        verify(chatModel, never()).stream(any(Prompt.class));
        assertThat(session.getPlayer().getFunctionAfterChat()).isNotNull();

        // 告别语播放期间的 listen 被 functionAfterChat 守卫挡掉：模式与 VAD 断句方式都不该被改
        device.listenStart(ListenMode.Manual);
        assertThat(session.getMode()).isEqualTo(ListenMode.Auto);
        assertThat(harness.vad().autoSegmentOf(session.getSessionId())).isTrue();
        assertThat(device.transport().isOpen()).isTrue();

        synthesizer.releasePlayback();
        AwaitHelper.until("告别语播完后会话被关闭", () -> !device.transport().isOpen());

        List<String> flow = outboundFlow();
        assertThat(flow).containsSubsequence("stt", "tts:start", "tts:sentence_start", "binary", "tts:stop");
        assertThat(Collections.frequency(flow, "tts:stop")).isEqualTo(1);
        // 关连接必须晚于 tts stop，设备才能收到播完的信号
        assertThat(flow.get(flow.size() - 1)).isEqualTo("tts:stop");
        assertThat(device.transport().closeStatus()).isNotNull();
        assertThat(harness.sessionManager().getSession(session.getSessionId())).isNull();
        // functionAfterChat 的收尾：Persona、Player、历史一并释放
        assertThat(session.getPersona()).isNull();
        assertThat(session.getPlayer()).isNull();
        assertThat(conversation.rawMessages()).isEmpty();
    }

    @Test
    void emptyRecognitionDoesNotStartTurnAndNextTurnStillWorks() {
        // 只有环境音，STT 终稿为空：本次聆听不成一轮
        harness.stt().withFinalText("");
        device.transport().clearOutbound();
        device.listenStart(ListenMode.Auto);
        speakOneSegment();
        AwaitHelper.until("空识别这轮已结束", () -> harness.stt().completedStreams() == 1);

        assertThat(conversation.rawMessages()).isEmpty();
        assertThat(device.transport().jsonSignatures()).doesNotContain("stt", "tts:start");

        runTurn("现在几点了", "刚过八点。");

        // 出站流水已在 runTurn 开头清过，这里的 stt 只可能来自第二次说话
        List<String> flow = outboundFlow();
        assertThat(flow).containsSubsequence("stt", "tts:start", "tts:sentence_start", "binary", "tts:stop");
        assertThat(Collections.frequency(flow, "stt")).isEqualTo(1);
        verify(chatModel, times(1)).stream(any(Prompt.class));
        assertThat(harness.stt().streamCalls()).isEqualTo(2);
        assertThat(texts(conversation.rawMessages())).containsExactly("现在几点了", "刚过八点。");
    }

    @Test
    void strayFrameAfterSegmentEndDoesNotJoinPreviousTurnStream() {
        runTurn("今天天气怎么样", "外面是晴天。");
        int framesAfterFirstTurn = harness.stt().receivedFrames().size();
        int vadFramesBefore = harness.vad().processedFrames().size();

        // 收句之后设备还在补发的尾音：VAD 仍是初始化状态，但本轮识别流已终结，帧不该再进任何识别流
        device.sendFrames(ScriptedVadService.SPEECH_CONTINUE, ScriptedVadService.SPEECH_CONTINUE);
        AwaitHelper.until("尾音帧已过 VAD", () -> harness.vad().processedFrames().size() == vadFramesBefore + 2);

        assertThat(harness.stt().receivedFrames()).hasSize(framesAfterFirstTurn);
        assertThat(harness.stt().streamCalls()).isEqualTo(1);
        assertThat(harness.stt().completedStreams()).isEqualTo(1);

        // 尾音没有污染链路，下一轮照常开新流
        runTurn("现在几点了", "刚过八点。");
        assertThat(harness.stt().streamCalls()).isEqualTo(2);
        assertThat(harness.stt().receivedFrames()).hasSize(framesAfterFirstTurn * 2);
        assertThat(texts(conversation.rawMessages()))
                .containsExactly("今天天气怎么样", "外面是晴天。", "现在几点了", "刚过八点。");
    }

    @Test
    void newTurnAfterAbortKeepsPreviousHistoryAndRunsClean() {
        runTurn("今天天气怎么样", "外面是晴天。");
        Sinks.Many<byte[]> firstSink = session.getAudioSinks();

        // 第二轮播到一半被设备打断
        synthesizer.holdPlayback();
        scriptedReplies.add("我来讲个故事。");
        harness.stt().withFinalText("讲个故事");
        device.transport().clearOutbound();
        device.listenStart(ListenMode.Auto);
        speakOneSegment();
        device.transport().awaitJson("tts:sentence_start");

        device.abort("wake_word_detected");
        synthesizer.releasePlayback();

        AwaitHelper.until("打断后播放器已清空", () -> !session.getPlayer().hasContent());
        List<String> abortedFlow = outboundFlow();
        assertThat(Collections.frequency(abortedFlow, "tts:stop")).isEqualTo(1);
        assertThat(session.getAudioSinks()).isNull();
        // 打断只收尾被打断的那一轮，更早那一轮的历史原样保留
        assertThat(texts(conversation.rawMessages()))
                .containsExactly("今天天气怎么样", "外面是晴天。", "讲个故事", "我来讲个故事。");

        runTurn("现在几点了", "刚过八点。");

        List<String> flow = outboundFlow();
        assertThat(flow).containsSubsequence("stt", "tts:start", "tts:sentence_start", "binary", "tts:stop");
        assertThat(Collections.frequency(flow, "tts:stop")).isEqualTo(1);
        assertThat(session.getAudioSinks()).isNotSameAs(firstSink);
        assertThat(session.getPlayer().spokenSentences()).containsExactly("刚过八点。");
        assertThat(types(conversation.rawMessages())).containsExactly(MessageType.USER, MessageType.ASSISTANT,
                MessageType.USER, MessageType.ASSISTANT, MessageType.USER, MessageType.ASSISTANT);
    }

    @Test
    void everyTurnEndsWithIdlePersonaAndOnePlaybackCompletedEvent() {
        runTurn("今天天气怎么样", "外面是晴天。");

        assertThat(session.getPersona().isActive()).isFalse();
        assertThat(harness.events().eventsOf(TtsPlaybackCompletedEvent.class)).hasSize(1);

        runTurn("现在几点了", "刚过八点。");

        // 轮次计数、合成器、播放队列全部归零，否则下一次开口会被当成打断而永远暂停
        assertThat(session.getPersona().isActive()).isFalse();
        assertThat(session.getPlayer().isPlaying()).isFalse();
        assertThat(session.getPlayer().isPaused()).isFalse();
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.LISTENING);
        // 播完一轮发一次，不重不漏
        assertThat(harness.events().eventsOf(TtsPlaybackCompletedEvent.class)).hasSize(2);
    }

    // ========== 驱动 ==========

    /** 驱动一轮完整对话：listen/start → 语音帧 → 服务端断句 → LLM → TTS，直到本轮播放收尾 */
    private void runTurn(String userText, String reply) {
        scriptedReplies.add(reply);
        harness.stt().withFinalText(userText);
        device.transport().clearOutbound();
        device.listenStart(ListenMode.Auto);
        speakOneSegment();
        device.transport().awaitJson("tts:stop");
        AwaitHelper.until("本轮播放已收尾", () -> !session.getPlayer().hasContent()
                && session.getDeviceState() == DeviceState.LISTENING);
    }

    /** 一段完整语音：起始、持续、结束三帧，auto 模式下服务端据此自动断句 */
    private void speakOneSegment() {
        device.speak(ScriptedVadService.SPEECH_START,
                ScriptedVadService.SPEECH_CONTINUE,
                ScriptedVadService.SPEECH_END);
    }

    /** 出站流水的可读形式：文本取 type[:state]，二进制帧统一记为 binary */
    private List<String> outboundFlow() {
        List<String> flow = new ArrayList<>();
        for (FakeWebSocketTransport.Sent sent : device.transport().snapshot()) {
            if (sent.binary()) {
                flow.add("binary");
                continue;
            }
            JsonNode node = readJson(sent.text());
            String type = node.path("type").asText("");
            String state = node.path("state").asText("");
            flow.add(state.isEmpty() ? type : type + ":" + state);
        }
        return flow;
    }

    private static JsonNode readJson(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("出站消息不是合法 JSON: " + text, e);
        }
    }

    private static List<String> texts(List<Message> messages) {
        return messages.stream().map(Message::getText).toList();
    }

    private static List<MessageType> types(List<Message> messages) {
        return messages.stream().map(Message::getMessageType).toList();
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text == null ? "" : text))));
    }

    /**
     * 真实 ScheduledPlayer + 可编排的 LLM/TTS/对话历史。不挂 OpusRecorder：本组用例不测录音与
     * AEC 参考，挂上只会往磁盘写 ogg。
     */
    private Persona buildPersona(ChatSession chatSession) {
        if (chatSession.getPersona() != null) {
            return chatSession.getPersona();
        }
        Player player = new ScheduledPlayer(chatSession, harness.messageSender());
        chatSession.setPlayer(player);
        synthesizer = new GatedSynthesizer(chatSession, player);
        conversation = new Conversation(chatSession.getDevice().getDeviceId(), 1,
                chatSession.getSessionId(), "协议测试角色", 1);
        Persona persona = Persona.builder()
                .sessionManager(harness.sessionManager())
                .sessionId(chatSession.getSessionId())
                .listener(personaListener)
                .sttService(harness.stt())
                .chatModel(chatModel)
                .synthesizer(synthesizer)
                .player(player)
                .conversation(conversation)
                .goodbyeMessages(new FixedGoodbyeMessages())
                .build();
        chatSession.setPersona(persona);
        return persona;
    }

    /** 告别语固定，便于断言下发的就是它 */
    private static final class FixedGoodbyeMessages extends GoodbyeMessageSupplier {
        @Override
        public String get() {
            return GOODBYE_TEXT;
        }
    }

    /**
     * 把 LLM 的 token 流按句落成预编码 opus 帧交给真实播放器。
     * {@link #holdPlayback()} 让这一句的帧发完后流不结束，制造一段"正在播"的确定窗口。
     */
    private static final class GatedSynthesizer extends Synthesizer {

        private volatile Disposable disposable;
        private volatile CountDownLatch gate;

        private GatedSynthesizer(ChatSession chatSession, Player player) {
            super(chatSession, null, player);
        }

        @Override
        public void synthesize(Flux<String> stringFlux) {
            disposable = stringFlux.subscribe(text -> speak(text, true), error -> {
            }, () -> {
            });
        }

        @Override
        public void synthesize(String text) {
            speak(text, false);
        }

        @Override
        public void cancel() {
            if (disposable != null) {
                disposable.dispose();
            }
        }

        @Override
        public boolean isActive() {
            return disposable != null && !disposable.isDisposed();
        }

        void holdPlayback() {
            gate = new CountDownLatch(1);
        }

        void releasePlayback() {
            CountDownLatch current = gate;
            gate = null;
            if (current != null) {
                current.countDown();
            }
        }

        private void speak(String text, boolean reply) {
            if (!StringUtils.hasText(text)) {
                return;
            }
            List<Speech> speeches = new ArrayList<>(FRAMES_PER_SENTENCE);
            for (int i = 0; i < FRAMES_PER_SENTENCE; i++) {
                byte[] opusFrame = new byte[40];
                opusFrame[0] = (byte) i;
                speeches.add(i == 0 ? Speech.ofOpus(opusFrame, text) : Speech.ofOpus(opusFrame));
            }
            Flux<Speech> speechFlux = Flux.fromIterable(speeches);
            CountDownLatch current = gate;
            if (current != null) {
                speechFlux = speechFlux.concatWith(Flux.defer(() -> {
                    awaitQuietly(current);
                    return Flux.<Speech>empty();
                }));
            }
            player.play(speechFlux, reply);
        }

        private static void awaitQuietly(CountDownLatch latch) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
