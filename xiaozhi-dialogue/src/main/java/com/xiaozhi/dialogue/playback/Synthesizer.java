package com.xiaozhi.dialogue.playback;

import com.xiaozhi.common.Speech;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.utils.AudioUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


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
     * 用兜底音色现场合成这句话。调用方在弹性线程或虚拟线程上，合成是阻塞的。
     *
     * @return 兜底也说不出来时返回空列表
     */
    protected List<Speech> fallbackSpeech(String text) {
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

    /**
     * 语音合成。
     * @param stringFlux 文本流，流的每一个元素来自于LLM的输出，主要是token。
     *                   一般不能直接提交进行TTS语音合成，需要由具体的Provider实现重整成句子再进行语音合成。
     */
    abstract public void synthesize(Flux<String> stringFlux);

    /**
     * 取消语音合成，停止上游Flux订阅。
     * 在用户打断（abort）时由外部调用，确保不再产生新的音频数据。
     */
    abstract public void cancel();

    /**
     * 检查语音合成管道是否仍在活跃（LLM生成中、TTS合成中等）。
     * 用于打断判断，即使Player已停止播放，如果上游管道仍在工作也应被打断。
     */
    abstract public boolean isActive();

    /**
     * 语音合成。
     * @param text 一般是完整的句子或者完整的可以一次性提交进行语音合成的文本。
     */
    abstract public void synthesize(String text);
}
