package com.xiaozhi.dialogue.playback;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.ai.tts.TtsService;

/**
 * Synthesizer 工厂，创建对应的 Synthesizer 实现。
 */
public class SynthesizerFactory {

    public static Synthesizer create(ChatSession session, TtsService ttsService, Player player) {
        return new FileSynthesizer(session, ttsService, player);
    }
}
