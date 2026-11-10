package com.xiaozhi.dialogue.playback;

import com.xiaozhi.common.Speech;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.ai.tts.SentenceHelper;
import com.xiaozhi.ai.tts.TtsOverloadException;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.utils.AudioUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
/**
 * 语音合成器，用于非流式TTS（先生成完整音频文件再播放）。
 * 适用于不支持流式输出的TTS Provider（如 SherpaOnnx）。
 *
 * 数据流：LLM token流 → SentenceHelper分句 → 逐句调用TTS生成完整音频文件 → 读取PCM → 交给播放器播放
 */
@Slf4j
public class FileSynthesizer extends Synthesizer {

    // 保存LLM输出流的订阅引用，以便在cancel时取消上游订阅
    private volatile Disposable llmDisposable;

    // 本轮角色音色已经失败过，后面的句子直接用兜底音色，不再每句都等一次失败
    private final AtomicBoolean fallbackEngaged = new AtomicBoolean();

    public FileSynthesizer(ChatSession session, TtsService ttsService, Player player, TtsService fallbackTts) {
        super(session, ttsService, player, fallbackTts);
    }

    @Override
    public void cancel() {
        if (llmDisposable != null && !llmDisposable.isDisposed()) {
            llmDisposable.dispose();
        }
    }

    @Override
    public boolean isActive() {
        return llmDisposable != null && !llmDisposable.isDisposed();
    }

    /**
     * 将LLM输出的token流转化为语音并推送到播放器。
     * 使用 SentenceHelper 按标点分句，逐句调用TTS生成完整音频文件后交给播放器。
     *
     * @param stringFlux LLM输出的token流
     */
    @Override
    public void synthesize(Flux<String> stringFlux) {
        synthesize(stringFlux, true);
    }

    /**
     * @param reply 是否本轮 LLM 回复，决定播放器是否把句子计入打断截断
     */
    private void synthesize(Flux<String> stringFlux, boolean reply) {
        fallbackEngaged.set(false);
        llmDisposable = new SentenceHelper().convert(stringFlux).subscribe(
            result -> {
                String text = result.text();
                String mood = result.mood();
                Flux<Speech> lazyTtsFlux = Flux.create(sink -> {
                    List<Speech> speech = sentenceSpeech(text);
                    if (!speech.isEmpty()) {
                        speech.getFirst().withMood(mood);
                        speech.forEach(sink::next);
                    }
                    sink.complete();
                });
                player.play(lazyTtsFlux, reply);
            },
            // 上游分句流出错时接住，否则 Reactor 会把异常丢回 LLM 的投递线程
            error -> log.error("分句流异常，本轮TTS合成中止 - SessionId: {}", chatSession.getSessionId(), error));
    }

    /**
     * 一句话的音频：先用角色音色合成；说不出来就改用兜底音色（本轮后面的句子也直接用它）。
     * 本地合成排队满了也走这条路：兜底音色是云端的，不占本地合成队列。
     */
    private List<Speech> sentenceSpeech(String text) {
        List<Speech> speech = fallbackEngaged.get() ? List.of() : roleSpeech(text);
        if (speech.isEmpty()) {
            fallbackEngaged.set(true);
            speech = fallbackSpeech(text);
        }
        if (speech.isEmpty()) {
            log.error("这句话没能说出来 - SessionId: {}", chatSession.getSessionId());
        }
        return speech;
    }

    /** 角色音色合成一句，失败返回空列表 */
    private List<Speech> roleSpeech(String text) {
        try {
            Path audioPath = ttsService.textToSpeech(text);
            if (audioPath == null) {
                log.error("TTS服务返回空音频文件，改用兜底音色 - SessionId: {}", chatSession.getSessionId());
                return List.of();
            }
            // 分块读取PCM，避免全量加载进内存
            List<byte[]> chunks = AudioUtils.readAsPcmChunks(audioPath.toString());
            List<Speech> speech = new ArrayList<>(chunks.size());
            for (byte[] chunk : chunks) {
                speech.add(speech.isEmpty() ? new Speech(chunk, text) : new Speech(chunk));
            }
            return speech;
        } catch (TtsOverloadException e) {
            log.warn("本地TTS过载，改用兜底音色: {} - SessionId: {}", e.getMessage(), chatSession.getSessionId());
            return List.of();
        } catch (Exception e) {
            log.error("TTS合成出错，改用兜底音色: {} - SessionId: {}", e.getMessage(), chatSession.getSessionId());
            return List.of();
        }
    }

    /**
     * 直接合成单个文本
     * @param text 待合成的文本
     */
    @Override
    public void synthesize(String text) {
        synthesize(Flux.just(text), false);
    }

}
