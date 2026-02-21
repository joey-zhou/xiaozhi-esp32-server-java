package com.xiaozhi.dialogue.service;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.utils.AudioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * 语音合成器，用于非流式TTS（先生成完整音频文件再播放）。
 * 适用于不支持流式输出的TTS Provider
 *
 * 数据流：LLM token流 → DialogueHelper分句 → 逐句调用TTS生成完整音频文件 → 读取PCM → 交给播放器播放
 */
public class FileSynthesizer extends Synthesizer {

    private static final Logger logger = LoggerFactory.getLogger(FileSynthesizer.class);

    // 保存LLM输出流的订阅引用，以便在cancel时取消上游订阅
    private volatile Disposable llmDisposable;

    private final TtsService ttsService;

    public FileSynthesizer(ChatSession session, TtsService ttsService, Player player) {
        super(session, player);
        this.ttsService = ttsService;
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
     * 使用 DialogueHelper 按标点分句，逐句调用TTS生成完整音频文件后交给播放器。
     *
     * @param stringFlux LLM输出的token流
     */
    @Override
    public void synthesize(Flux<String> stringFlux) {
        llmDisposable = new DialogueHelper().convert(stringFlux).subscribe(text -> {
            Flux<Speech> lazyTtsFlux = Flux.create(sink -> {
                try {
                    String audioPath = ttsService.textToSpeech(text);
                    if (audioPath != null) {
                        byte[] audioData = AudioUtils.readAsPcm(audioPath);
                        sink.next(new Speech(audioData, text));
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
    public void synthesize(String text){
        synthesize(Flux.just(text));
    }

}
