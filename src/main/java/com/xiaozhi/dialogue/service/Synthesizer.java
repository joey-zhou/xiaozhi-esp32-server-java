package com.xiaozhi.dialogue.service;

import com.xiaozhi.communication.common.ChatSession;
import lombok.Data;
import reactor.core.publisher.Flux;


/**
 * 用于存放一轮对话（即一问一答）过程中的状态数据。
 * 阶段：VAD -> STT -> LLM -> TTS -> Player
 * Synthesizer 实际是属于TTS阶段，是对TTS 不同Provider的封装。不同TTS Provider返回的数据格式不同，而Player与终端设备约定的数据格式通常只能一种。
 * 而从wav-> opus的转换是更应该发生在 Player。
 * 格式转换时原始形态的RAW数据可以很大，但网络传输及在内存缓冲区等候传输则应该尽可能是压缩后的，以便减少对内存资源的占用。
 * ChatModel输出的是有顺序的文本， Player播放的是有顺序的音频流。在Synthesizer拆解的句子也应该由Synthesizer恢复原来的顺序。
 *
 * 理想的情况：
 * var textFlux = chatModel.stream(prompt);
 * var audioFlux = synthesizer.synthesize(textFlux);
 * var player = player.play(audioFlux);
 *
 * chatModel与 LLM配置有关，每个虚拟Role的LLM配置是确定的。
 * synthesizer与TTS配置有关，每个虚拟Role的TTS配置是确定的。基于这个考虑，也可以做成非一次性的，
 * 播放器与终端设备有关，每个播放器与网络连接是绑定的。
 */
@Data
public abstract class Synthesizer {

    protected final ChatSession chatSession;
    protected final Player player;

    private int firstChatDurationMillis = 0;

    /**
     * 构造函数。语音合成器、播放器的生命周期是可以不一样的。
     * 当语音合成器完成了一轮对话里AI响应的全部文本的合成，此对象就可以丢弃（被垃圾回收）。而此时的播放器很可能还正在播放。
     * 而且同一个语音合成器，可以根据场景组合不同的播放器。所以播放器不在语音合成器内部new对象。
     * @param chatSession 当前会话
     * @param player 播放音频的播放器
     */
    public Synthesizer(ChatSession chatSession, Player player) {
        this.chatSession = chatSession;
        this.player = player;
    }

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

    /**
     * 语音合成。
     * @param stringFlux 文本流，流的每一个元素来自于LLM的输出，主要是token。
     *                   一般不能直接提交进行TTS语音合成，需要由具体的Provider实现重整成句子再进行语音合成。
     */
    abstract public void synthesize(Flux<String> stringFlux);

}
