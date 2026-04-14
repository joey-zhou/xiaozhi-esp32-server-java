package com.xiaozhi.dialogue.playback;

import com.xiaozhi.common.Speech;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.ai.tts.SentenceHelper;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.utils.AudioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.List;

/**
 * 语音合成器，用于非流式TTS（先生成完整音频文件再播放）。
 * 适用于不支持流式输出的TTS Provider（如 SherpaOnnx）。
 *
 * 数据流：LLM token流 → SentenceHelper分句 → 逐句调用TTS生成完整音频文件 → 读取PCM → 交给播放器播放
 */
public class FileSynthesizer extends Synthesizer {

    private static final Logger logger = LoggerFactory.getLogger(FileSynthesizer.class);

    // 保存LLM输出流的订阅引用，以便在cancel时取消上游订阅
    private volatile Disposable llmDisposable;

    public FileSynthesizer(ChatSession session, TtsService ttsService, Player player) {
        super(session, ttsService, player);
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
        llmDisposable = new SentenceHelper().convert(stringFlux).subscribe(text -> {
            Flux<Speech> lazyTtsFlux = Flux.create(sink -> {
                try {
                    Path audioPath = ttsService.textToSpeech(text);
                    if (audioPath != null) {
                        List<byte[]> chunks = AudioUtils.readAsPcmChunks(audioPath.toString());
                        boolean first = true;
                        for (byte[] chunk : chunks) {
                            sink.next(first ? new Speech(chunk, text) : new Speech(chunk));
                            first = false;
                        }
                    } else {
                        logger.error("TTS服务返回空音频文件 - SessionId: {}", chatSession.getSessionId());
                    }
                } catch (Exception e) {
                    logger.error("TTS合成出错: {} - SessionId: {}", e.getMessage(), chatSession.getSessionId());
                }
                sink.complete();
            });
            player.play(lazyTtsFlux);
        });
    }

    /**
     * 直接合成单个文本
     * @param text 待合成的文本
     */
    @Override
    public void synthesize(String text) {
        // 委托给 synthesize(Flux) 处理，缓存指标在那里统一记录
        synthesize(Flux.just(text));
    }

}
