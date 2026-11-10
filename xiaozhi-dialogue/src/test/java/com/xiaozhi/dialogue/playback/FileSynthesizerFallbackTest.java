package com.xiaozhi.dialogue.playback;

import com.xiaozhi.ai.tts.TtsOverloadException;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.utils.AudioUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色音色说不出来时先换兜底音色把这句说出来，本轮后面的句子也直接用兜底音色；下一轮照常先试角色音色。
 */
class FileSynthesizerFallbackTest {

    // 分句器有 8 字最小长度，短于它的两句会被并成一句，用例拿不到两次下发
    private static final String SENTENCE = "今天天气不错，出门走走吧。";
    private static final String SECOND_SENTENCE = "后面这句也要单独成句。";

    @TempDir
    Path tempDir;

    private ChatSession session;
    private Player player;
    private TtsService ttsService;
    private TtsService fallbackTts;

    /** 播放器收到的全部下发流，按提交顺序 */
    private final List<Flux<Speech>> played = new ArrayList<>();

    @BeforeEach
    void setUp() {
        session = mock(ChatSession.class);
        player = mock(Player.class);
        ttsService = mock(TtsService.class);
        fallbackTts = mock(TtsService.class);
        doAnswer(invocation -> {
            played.add(invocation.getArgument(0));
            return null;
        }).when(player).play(any(), anyBoolean());
    }

    @Test
    void roleFailureSpeaksSentenceInFallbackVoice() throws Exception {
        when(ttsService.textToSpeech(SENTENCE)).thenThrow(new IllegalStateException("音色已下线"));
        when(fallbackTts.textToSpeech(SENTENCE)).thenReturn(wav("fallback.wav"));

        List<Speech> spoken = synthesizeAndCollect(newSynthesizer(fallbackTts), SENTENCE);

        assertThat(spoken).isNotEmpty();
        assertThat(spoken.getFirst().getText()).isEqualTo(SENTENCE);
    }

    /** 本地合成排队满了也换兜底音色，不占本地合成队列 */
    @Test
    void overloadSpeaksSentenceInFallbackVoice() throws Exception {
        when(ttsService.textToSpeech(SENTENCE)).thenThrow(new TtsOverloadException("排队已满"));
        when(fallbackTts.textToSpeech(SENTENCE)).thenReturn(wav("fallback.wav"));

        assertThat(synthesizeAndCollect(newSynthesizer(fallbackTts), SENTENCE))
                .extracting(Speech::getText).first().isEqualTo(SENTENCE);
    }

    @Test
    void emptyAudioPathSpeaksSentenceInFallbackVoice() throws Exception {
        when(ttsService.textToSpeech(SENTENCE)).thenReturn(null);
        when(fallbackTts.textToSpeech(SENTENCE)).thenReturn(wav("fallback.wav"));

        assertThat(synthesizeAndCollect(newSynthesizer(fallbackTts), SENTENCE))
                .extracting(Speech::getText).first().isEqualTo(SENTENCE);
    }

    @Test
    void noFallbackVoiceStaysSilent() throws Exception {
        when(ttsService.textToSpeech(SENTENCE)).thenThrow(new TtsOverloadException("排队已满"));

        assertThat(synthesizeAndCollect(newSynthesizer(null), SENTENCE)).isEmpty();
    }

    @Test
    void bothVoicesFailingStaysSilent() throws Exception {
        when(ttsService.textToSpeech(SENTENCE)).thenThrow(new IllegalStateException("连不上"));
        when(fallbackTts.textToSpeech(SENTENCE)).thenThrow(new IllegalStateException("连不上"));

        assertThat(synthesizeAndCollect(newSynthesizer(fallbackTts), SENTENCE)).isEmpty();
    }

    @Test
    void fallbackStaysEngagedForRestOfTurn() throws Exception {
        when(ttsService.textToSpeech(SENTENCE)).thenThrow(new IllegalStateException("音色已下线"));
        when(fallbackTts.textToSpeech(SENTENCE)).thenReturn(wav("first.wav"));
        when(fallbackTts.textToSpeech(SECOND_SENTENCE)).thenReturn(wav("second.wav"));

        newSynthesizer(fallbackTts).synthesize(Flux.just(SENTENCE, SECOND_SENTENCE));

        assertThat(played).hasSize(2);
        assertThat(collect(played.get(0))).extracting(Speech::getText).first().isEqualTo(SENTENCE);
        assertThat(collect(played.get(1))).extracting(Speech::getText).first().isEqualTo(SECOND_SENTENCE);
        // 第一句失败后本轮不再等角色音色失败第二次
        verify(ttsService, never()).textToSpeech(SECOND_SENTENCE);
    }

    @Test
    void nextTurnTriesRoleVoiceAgain() throws Exception {
        when(ttsService.textToSpeech(SENTENCE)).thenThrow(new IllegalStateException("网络抖动"));
        when(fallbackTts.textToSpeech(SENTENCE)).thenReturn(wav("fallback.wav"));
        when(ttsService.textToSpeech(SECOND_SENTENCE)).thenReturn(wav("role.wav"));
        FileSynthesizer synthesizer = newSynthesizer(fallbackTts);

        synthesizer.synthesize(Flux.just(SENTENCE));
        collect(played.get(0));
        synthesizer.synthesize(Flux.just(SECOND_SENTENCE));

        assertThat(collect(played.get(1))).extracting(Speech::getText).first().isEqualTo(SECOND_SENTENCE);
        verify(ttsService).textToSpeech(SECOND_SENTENCE);
        verify(fallbackTts, never()).textToSpeech(SECOND_SENTENCE);
    }

    private List<Speech> synthesizeAndCollect(FileSynthesizer synthesizer, String text) {
        synthesizer.synthesize(Flux.just(text));
        assertThat(played).hasSize(1);
        return collect(played.getFirst());
    }

    private FileSynthesizer newSynthesizer(TtsService fallback) {
        return new FileSynthesizer(session, ttsService, player, fallback);
    }

    private static List<Speech> collect(Flux<Speech> flux) {
        List<Speech> collected = flux.collectList().block();
        return collected == null ? List.of() : collected;
    }

    private Path wav(String name) {
        Path path = tempDir.resolve(name);
        AudioUtils.saveAsWav(path, new byte[3840 * 2]);
        return path;
    }
}
