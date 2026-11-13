package com.xiaozhi.dialogue.playback;

import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.utils.AudioUtils;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.util.List;

/**
 * 语音合成器，用于非流式TTS（先生成完整音频文件再播放）。
 * 适用于不支持流式输出的TTS Provider（如 SherpaOnnx）。
 *
 * 数据流：LLM token流 → SentenceHelper分句 → 逐句调用TTS生成完整音频文件 → 读取PCM → 交给播放器播放
 */
@Slf4j
public class FileSynthesizer extends Synthesizer {

    public FileSynthesizer(ChatSession session, TtsService ttsService, Player player, TtsService fallbackTts) {
        super(session, ttsService, player, fallbackTts);
    }

    /** 整句合成成文件再分块读出；合成是阻塞的，放到弹性线程上。本地合成排队满了也按失败处理，由基类换兜底音色 */
    @Override
    protected Flux<Speech> roleSentence(String text) {
        return Flux.defer(() -> {
                    try {
                        Path audioPath = ttsService.textToSpeech(text);
                        if (audioPath == null) {
                            log.error("TTS服务返回空音频文件 - SessionId: {}", chatSession.getSessionId());
                            return Flux.<Speech>empty();
                        }
                        // 分块读取PCM，避免全量加载进内存
                        List<byte[]> chunks = AudioUtils.readAsPcmChunks(audioPath.toString());
                        return Flux.fromIterable(chunks).map(Speech::new);
                    } catch (Exception e) {
                        return Flux.<Speech>error(e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
