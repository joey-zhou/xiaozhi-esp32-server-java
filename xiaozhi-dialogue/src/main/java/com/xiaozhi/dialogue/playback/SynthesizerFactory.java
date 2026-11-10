package com.xiaozhi.dialogue.playback;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.ai.tts.TtsService;

/**
 * Synthesizer 工厂，创建对应的 Synthesizer 实现。
 */
public class SynthesizerFactory {

    /**
     * @param fallbackTts 角色音色说不出来时改用的音色，可为 null
     */
    public static Synthesizer create(ChatSession session, TtsService ttsService, Player player,
                                     TtsService fallbackTts) {
        return new FileSynthesizer(session, ttsService, player, fallbackTts);
    }
}
