package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.tool.ToolsSessionHolder;
import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.Synthesizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 被打断的回复按"用户听到了什么"进历史：正在下发的那句算听到，
 * 一个字没播出就只留用户消息，已完成但没播完的回头截断。
 */
@ExtendWith(MockitoExtension.class)
class PersonaInterruptTest {

    @Mock
    private SessionManager sessionManager;
    @Mock
    private PersonaListener listener;
    @Mock
    private ChatModel chatModel;
    @Mock
    private ToolsSessionHolder toolsSessionHolder;
    @Mock
    private MessageSender messageSender;

    private WebSocketSession session;
    private Conversation conversation;
    private FakePlayer player;
    private FakeSynthesizer synthesizer;
    private Persona persona;

    @BeforeEach
    void setUp() {
        session = new WebSocketSession("s1");
        session.setToolsSessionHolder(toolsSessionHolder);
        lenient().when(toolsSessionHolder.getAllFunction()).thenReturn(List.of());
        lenient().when(sessionManager.getSession("s1")).thenReturn(session);
        conversation = new Conversation("device", 1, "s1", "role", 1);
        player = new FakePlayer(session, messageSender);
        synthesizer = new FakeSynthesizer(session, player);
        persona = Persona.builder()
                .sessionManager(sessionManager)
                .sessionId("s1")
                .listener(listener)
                .chatModel(chatModel)
                .synthesizer(synthesizer)
                .player(player)
                .conversation(conversation)
                .build();
        session.setPersona(persona);
        session.setPlayer(player);
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** LLM 吐完 text 后挂起不完成，模拟还在生成 */
    private void llmStreaming(String text) {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.concat(Flux.just(response(text)), Flux.never()));
    }

    private void llmCompleted(String text) {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response(text)));
    }

    private static List<String> texts(List<Message> messages) {
        return messages.stream().map(Message::getText).toList();
    }

    @Test
    void interruptedWhileGeneratingKeepsSpokenPart() {
        llmStreaming("从前有座山。山里有座庙。庙里有个老和尚。");
        UserMessage user = new UserMessage("讲个故事");
        persona.chat(user, false);
        player.speak("从前有座山。");
        player.speak("山里有座庙。");

        persona.onInterrupted();

        assertThat(texts(conversation.rawMessages())).containsExactly("讲个故事", "从前有座山。山里有座庙。");
        ArgumentCaptor<DialogueTurn> captor = ArgumentCaptor.forClass(DialogueTurn.class);
        verify(listener).onDialogueTurn(captor.capture());
        assertThat(captor.getValue().getUserMessage()).isSameAs(user);
        assertThat(captor.getValue().getAssistantMessage().getText()).isEqualTo("从前有座山。山里有座庙。");
        assertThat(captor.getValue().isInterrupted()).isTrue();
    }

    @Test
    void interruptedBeforeAnySpeechKeepsOnlyUserMessage() {
        llmStreaming("从前有座山。");
        persona.chat(new UserMessage("讲个故事"), false);

        persona.onInterrupted();

        assertThat(texts(conversation.rawMessages())).containsExactly("讲个故事");
        ArgumentCaptor<DialogueTurn> captor = ArgumentCaptor.forClass(DialogueTurn.class);
        verify(listener).onDialogueTurn(captor.capture());
        assertThat(captor.getValue().getAssistantMessage()).isNull();
        assertThat(captor.getValue().isInterrupted()).isTrue();
    }

    @Test
    void nextTurnSeesBothUserMessages() {
        llmStreaming("从前有座山。");
        persona.chat(new UserMessage("讲个故事"), false);
        persona.onInterrupted();

        llmCompleted("好的，换一个。");
        persona.chat(new UserMessage("换一个"), false);

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).stream(prompt.capture());
        List<String> userTurns = prompt.getValue().getInstructions().stream()
                .filter(m -> m.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .toList();
        // 投影层会给用户消息加时间戳前缀，只看结尾
        assertThat(userTurns).hasSize(2);
        assertThat(userTurns.get(0)).endsWith("讲个故事");
        assertThat(userTurns.get(1)).endsWith("换一个");
        assertThat(texts(conversation.rawMessages())).containsExactly("讲个故事", "换一个", "好的，换一个。");
    }

    @Test
    void completedTurnCutMidPlaybackIsTruncated() {
        llmCompleted("第一句。第二句。第三句。");
        persona.chat(new UserMessage("说三句"), false);
        assertThat(texts(conversation.rawMessages())).containsExactly("说三句", "第一句。第二句。第三句。");
        player.speak("第一句。");
        player.speak("第二句。");
        // 第三句还在队列里
        player.setContent(true);

        persona.onInterrupted();

        assertThat(texts(conversation.rawMessages())).containsExactly("说三句", "第一句。第二句。");
        verify(listener).onDialogueTurnTruncated(eq(conversation), any(Instant.class), eq("第一句。第二句。"));
    }

    @Test
    void completedTurnCutBeforeAnySpeechIsRemoved() {
        llmCompleted("很短。");
        persona.chat(new UserMessage("嗯"), false);

        // 双向 TTS 首帧未到：合成器仍活跃，播放器尚未注册，用户一个字没听到
        synthesizer.forceActive(true);
        persona.onInterrupted();

        assertThat(texts(conversation.rawMessages())).containsExactly("嗯");
        verify(listener).onDialogueTurnTruncated(eq(conversation), any(Instant.class), eq(""));
    }

    @Test
    void fullyPlayedTurnIsUntouchedByLaterInterrupt() {
        llmCompleted("第一句。第二句。");
        persona.chat(new UserMessage("说两句"), false);
        player.speak("第一句。");
        player.speak("第二句。");
        player.finishPlayback();

        // 之后问候语在播时被打断，与本轮无关
        player.speakOther("你好，我在。");
        player.setContent(true);
        persona.onInterrupted();

        assertThat(texts(conversation.rawMessages())).containsExactly("说两句", "第一句。第二句。");
        verify(listener, never()).onDialogueTurnTruncated(any(), any(), anyString());
    }

    @Test
    void pendingTurnInterruptedRecordsUserOnly() {
        long epoch = persona.prepareTurn();
        assertThat(persona.isActive()).isTrue();

        persona.markInterrupted();
        persona.chat(new UserMessage("今天天气"), false, epoch);
        persona.releaseTurn();

        verify(chatModel, never()).stream(any(Prompt.class));
        assertThat(texts(conversation.rawMessages())).containsExactly("今天天气");
        ArgumentCaptor<DialogueTurn> captor = ArgumentCaptor.forClass(DialogueTurn.class);
        verify(listener).onDialogueTurn(captor.capture());
        assertThat(captor.getValue().getAssistantMessage()).isNull();
        assertThat(persona.isActive()).isFalse();
    }

    @Test
    void toolChainsFinishedBeforeInterruptAreKept() {
        llmStreaming("查到了，今天晴。");
        persona.chat(new UserMessage("天气"), false);
        session.getDialogueContext().addToolChain(null,
                AssistantMessage.builder()
                        .toolCalls(List.of(new AssistantMessage.ToolCall("c1", "function", "weather", "{}")))
                        .build(),
                ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse("c1", "weather", "晴")))
                        .build());
        player.speak("查到了，今天晴。");

        persona.onInterrupted();

        List<Message> history = conversation.rawMessages();
        assertThat(history).hasSize(4);
        assertThat(history.get(1).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(((AssistantMessage) history.get(1)).getToolCalls()).hasSize(1);
        assertThat(history.get(2).getMessageType()).isEqualTo(MessageType.TOOL);
        assertThat(history.get(3).getText()).isEqualTo("查到了，今天晴。");
        ArgumentCaptor<DialogueTurn> captor = ArgumentCaptor.forClass(DialogueTurn.class);
        verify(listener).onDialogueTurn(captor.capture());
        assertThat(captor.getValue().getToolChains()).hasSize(1);
    }

    @Test
    void secondInterruptIsNoOp() {
        llmStreaming("第一句。");
        persona.chat(new UserMessage("说"), false);
        player.speak("第一句。");
        persona.onInterrupted();

        persona.onInterrupted();

        verify(listener, times(1)).onDialogueTurn(any());
        assertThat(texts(conversation.rawMessages())).containsExactly("说", "第一句。");
    }

    @Test
    void nonReplySentencesAreFilteredOut() {
        llmStreaming("从前有座山。山里有座庙。");
        persona.chat(new UserMessage("讲个故事"), false);
        // 工具友好提示也走 sentence_start，但不是 LLM 回复
        player.speakOther("让我想想。");
        player.speak("从前有座山。");

        persona.onInterrupted();

        assertThat(texts(conversation.rawMessages())).containsExactly("讲个故事", "从前有座山。");
    }

    @Test
    void llmErrorClosesTurnAndPersistsUserOnly() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(new IllegalStateException("LLM 挂了")));
        persona.chat(new UserMessage("你好"), false);

        verify(listener).onError(any());
        ArgumentCaptor<DialogueTurn> captor = ArgumentCaptor.forClass(DialogueTurn.class);
        verify(listener).onDialogueTurn(captor.capture());
        assertThat(captor.getValue().getAssistantMessage()).isNull();
        assertThat(texts(conversation.rawMessages())).containsExactly("你好");

        // 兜底口播在播时被打断，与本轮无关
        player.speakOther("抱歉，我刚刚走神了，你能再说一遍吗？");
        persona.onInterrupted();

        verify(listener, times(1)).onDialogueTurn(any());
        verify(listener, never()).onDialogueTurnTruncated(any(), any(), anyString());
        assertThat(texts(conversation.rawMessages())).containsExactly("你好");
    }

    @Test
    void fullyDeliveredButStopNotYetSentIsNotRewritten() {
        llmCompleted("**第一句**。第二句。");
        persona.chat(new UserMessage("说两句"), false);
        player.speak("第一句。");
        player.speak("第二句。");

        // 全部帧已下发、合成器空闲，只差 120ms 后的 tts stop
        persona.onInterrupted();

        assertThat(texts(conversation.rawMessages())).containsExactly("说两句", "**第一句**。第二句。");
        verify(listener, never()).onDialogueTurnTruncated(any(), any(), anyString());
    }

    @Test
    void stopWhileSynthesizerStillActiveDoesNotCloseTurn() {
        llmCompleted("第一句。第二句。");
        persona.chat(new UserMessage("说两句"), false);
        // 工具提示播完触发了一次 tts stop，但回复的 TTS 还在回音频
        synthesizer.forceActive(true);
        player.finishPlayback();

        player.speak("第一句。");
        player.setContent(true);
        persona.onInterrupted();

        assertThat(texts(conversation.rawMessages())).containsExactly("说两句", "第一句。");
        verify(listener).onDialogueTurnTruncated(eq(conversation), any(Instant.class), eq("第一句。"));
    }

    @Test
    void asyncInterruptTargetsTurnCurrentAtTriggerTime() {
        llmStreaming("从前有座山。");
        persona.chat(new UserMessage("讲个故事"), false);
        player.speak("从前有座山。");
        // 第二句首字到达：同步记下目标
        persona.markInterrupted();
        // 异步 abort 还没跑，第二句已经进了 chat
        long epoch = persona.prepareTurn();
        persona.releaseTurn();
        llmCompleted("好的，换一个。");
        persona.chat(new UserMessage("换一个"), false, epoch);

        persona.onInterrupted();

        assertThat(texts(conversation.rawMessages()))
                .containsExactly("讲个故事", "从前有座山。", "换一个", "好的，换一个。");
    }

    /** 不真的发帧，只暴露"某句开始下发"与"播完"两个动作 */
    static class FakePlayer extends Player {
        private volatile boolean content;

        FakePlayer(WebSocketSession session, MessageSender sender) {
            super(session, sender);
        }

        @Override
        public void play(Flux<Speech> speechFlux, boolean reply) {
        }

        /** 本轮 LLM 回复的一句开始下发 */
        void speak(String sentence) {
            sendSentenceStart(sentence, true);
        }

        /** 问候语、推送、工具提示这类非回复句子开始下发 */
        void speakOther(String sentence) {
            sendSentenceStart(sentence, false);
        }

        /** 队列里还有没下发完的帧 */
        void setContent(boolean content) {
            this.content = content;
        }

        void finishPlayback() {
            content = false;
            sendStop();
        }

        @Override
        public boolean hasContent() {
            return content;
        }

        @Override
        public boolean isDrained() {
            return !content;
        }
    }

    /** 只订阅 token 流，把订阅句柄交给 cancel/isActive；forceActive 模拟 TTS 仍在回音频 */
    static class FakeSynthesizer extends Synthesizer {
        private volatile Disposable disposable;
        private volatile boolean forceActive;

        FakeSynthesizer(WebSocketSession session, Player player) {
            super(session, null, player);
        }

        @Override
        public void synthesize(Flux<String> stringFlux) {
            disposable = stringFlux.subscribe(t -> { }, e -> { }, () -> { });
        }

        @Override
        public void cancel() {
            if (disposable != null) {
                disposable.dispose();
            }
        }

        @Override
        public boolean isActive() {
            return forceActive || (disposable != null && !disposable.isDisposed());
        }

        void forceActive(boolean active) {
            this.forceActive = active;
        }

        @Override
        public void synthesize(String text) {
        }
    }
}
