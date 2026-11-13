package com.xiaozhi.dialogue.playback;

import com.xiaozhi.ai.tts.SentenceHelper;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.utils.AudioUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 语音合成器基类，TtsService 的高层编排器。
 *
 * 在对话管道 VAD → STT → LLM → TTS → Player 中，Synthesizer 属于 TTS 阶段，
 * 负责将 LLM 输出的文本流（或单个文本）编排为 Player 可消费的 Speech 音频流。
 * 不同 TTS Provider 返回的数据格式不同，
 * Synthesizer 屏蔽了这些差异，统一向 Player 提供 Flux<Speech>。
 *
 * 架构层级：
 * - TtsService：底层 TTS Provider 接口
 * - Synthesizer（本类）：高层编排器，负责分句、缓存查询、音频流构建
 * - Player：终端音频播放，接收 Flux<Speech>，转换为设备协议格式（Opus）发送
 *
 * 合成模式：
 * text → Path（FileSynthesizer + TtsService）
 *
 * 生命周期：Synthesizer 在一轮对话的 AI 响应合成完毕后即可回收，Player 可能仍在播放。
 * 同一个 Synthesizer 可组合不同的 Player，因此 Player 不在 Synthesizer 内部创建。
 */
@Data
@Slf4j
public abstract class Synthesizer {

    protected final ChatSession chatSession;
    protected final TtsService ttsService;
    protected final Player player;

    private int firstChatDurationMillis = 0;

    /**
     * 角色的语音合成说不出来时改用的音色（默认 Edge 与默认音色），为 null 表示没有兜底。
     * 只在出错的这一轮里临时换声音，下一轮照常先试角色自己的
     */
    protected final TtsService fallbackTts;

    /** 当前这一轮回复，打断时 cancel 的就是它 */
    private volatile SynthesisRound currentRound;

    /**
     * @param chatSession  当前会话
     * @param ttsService   底层 TTS Provider（由 TtsServiceFactory 创建）
     * @param player       播放音频的播放器
     * @param fallbackTts  兜底音色，可为 null
     */
    public Synthesizer(ChatSession chatSession, TtsService ttsService, Player player, TtsService fallbackTts) {
        this.chatSession = chatSession;
        this.ttsService = ttsService;
        this.player = player;
        this.fallbackTts = fallbackTts;
    }

    /**
     * 一轮合成的状态。本轮回复每次新建一份；单句（问候、告别、提示）用自己的一份，
     * 不能改写本轮回复的，否则回复中途插的一句会把本轮的兜底状态冲掉
     */
    protected static final class SynthesisRound {
        /** 本轮角色音色已经失败过，后面的句子直接用兜底音色，不再每句都等一次失败 */
        final AtomicBoolean fallbackEngaged = new AtomicBoolean();
        /** 本轮对上游文本流的订阅，打断时断开它，就不会再有新句子进来 */
        volatile Disposable upstream;
    }

    /**
     * 一轮回复。
     *
     * @param stringFlux 文本流，每个元素来自 LLM 的输出（多是 token），按标点分句后逐句合成
     */
    public void synthesize(Flux<String> stringFlux) {
        SynthesisRound round = new SynthesisRound();
        currentRound = round;
        round.upstream = new SentenceHelper().convert(stringFlux).subscribe(
            result -> player.play(sentence(result.text(), result.mood(), round), true),
            // 上游分句流出错时接住，否则 Reactor 会把异常丢回 LLM 的投递线程
            error -> log.error("分句流异常，本轮TTS合成中止 - SessionId: {}", chatSession.getSessionId(), error));
    }

    /**
     * 单句（问候、告别、故障提示）。用自己的轮次状态，不接管本轮回复的订阅：
     * 回复中途插的一句不能让打断停不住回复本身
     */
    public void synthesize(String text) {
        player.play(sentence(text, null, new SynthesisRound()), false);
    }

    /** 断开本轮回复的上游，不再产生新句子。已经排进播放队列的由播放器停 */
    public void cancel() {
        SynthesisRound round = currentRound;
        if (round != null && round.upstream != null) {
            round.upstream.dispose();
        }
    }

    /** 本轮回复的上游是否还在出字。打断判断用：播放器停了，上游还在工作也要打断 */
    public boolean isActive() {
        SynthesisRound round = currentRound;
        return round != null && round.upstream != null && !round.upstream.isDisposed();
    }

    /**
     * 用角色音色说一句。说不出来就抛错或给空流，由基类换兜底音色。
     * 首帧的文本与表情由基类挂上
     */
    protected abstract Flux<Speech> roleSentence(String text);

    /**
     * 一句话的音频流。各句在出字时就排进播放队列、轮到它才订阅：走哪个音色要在订阅那一刻决定，
     * 否则前一句失败之前就排好的句子还会再去等一次失败
     */
    protected Flux<Speech> sentence(String text, String mood, SynthesisRound round) {
        return Flux.defer(() -> round.fallbackEngaged.get()
                ? fallback(text, mood)
                : roleThenFallback(text, mood, round));
    }

    /** 角色音色一帧都没出就失败时换兜底音色重说这句，本轮后面的句子也直接用它；说了半句再重说会重复 */
    private Flux<Speech> roleThenFallback(String text, String mood, SynthesisRound round) {
        AtomicBoolean emitted = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        Flux<Speech> role = roleSentence(text)
                .doOnNext(speech -> {
                    if (emitted.compareAndSet(false, true)) {
                        speech.withText(text).withMood(mood);
                    }
                })
                .onErrorResume(error -> {
                    if (error instanceof CancellationException) {
                        cancelled.set(true);
                    } else if (emitted.get()) {
                        log.error("TTS合成中途出错，这句只说了一半 - SessionId: {}", chatSession.getSessionId(), error);
                    } else {
                        log.error("角色音色没能说出这句，改用兜底音色 - SessionId: {}", chatSession.getSessionId(), error);
                    }
                    return Flux.empty();
                });
        return role.concatWith(Flux.defer(() -> {
            if (emitted.get() || cancelled.get()) {
                return Flux.empty();
            }
            round.fallbackEngaged.set(true);
            return fallback(text, mood);
        }));
    }

    /** 兜底音色说这句 */
    private Flux<Speech> fallback(String text, String mood) {
        return Flux.defer(() -> {
                    List<Speech> speech = fallbackSpeech(text);
                    if (speech.isEmpty()) {
                        log.error("这句话没能说出来 - SessionId: {}", chatSession.getSessionId());
                        return Flux.<Speech>empty();
                    }
                    speech.getFirst().withMood(mood);
                    return Flux.fromIterable(speech);
                })
                // 兜底合成是阻塞的，不能压在上游 TTS 的回调线程上
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 用兜底音色现场合成这句话。
     *
     * @return 兜底也说不出来时返回空列表
     */
    private List<Speech> fallbackSpeech(String text) {
        if (fallbackTts == null || !StringUtils.hasText(text)) {
            return List.of();
        }
        try {
            Path audioPath = fallbackTts.textToSpeech(text);
            if (audioPath == null) {
                return List.of();
            }
            List<byte[]> chunks = AudioUtils.readAsPcmChunks(audioPath.toString());
            List<Speech> speech = new ArrayList<>(chunks.size());
            for (byte[] chunk : chunks) {
                speech.add(speech.isEmpty() ? new Speech(chunk, text) : new Speech(chunk));
            }
            return speech;
        } catch (Exception e) {
            log.warn("兜底音色也没能合成 - SessionId: {}, 原因: {}", chatSession.getSessionId(), e.getMessage());
            return List.of();
        }
    }
}
