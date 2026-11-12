package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.playback.FaultNotice;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.Synthesizer;
import com.xiaozhi.dialogue.playback.SynthesizerFactory;
import com.xiaozhi.enums.DeviceState;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 出故障时给用户一句话。识别、模型坏掉时设备完全不吭声，用户只会觉得「音箱坏了」。
 */
@Slf4j
@Component
public class FaultNotifier {

    /** 同一会话两次故障提示的最短间隔 */
    static final Duration NOTICE_INTERVAL = Duration.ofSeconds(30);

    @Resource
    private TtsServiceFactory ttsFactory;

    /**
     * 有角色就用角色的声音；会话没建起来（没有 Persona）时用默认音色。
     * 刚播过一句、连接已断、或者还没有播放器时不播
     */
    public void notice(ChatSession session, FaultNotice notice) {
        if (!session.isAudioChannelOpen()) {
            return;
        }
        Player player = session.getPlayer();
        if (player == null) {
            log.warn("会话没有播放器，故障提示无法播放 - SessionId: {}, 类别: {}", session.getSessionId(), notice);
            return;
        }
        if (!session.getDialogueContext().claimFaultNotice(NOTICE_INTERVAL)) {
            return;
        }
        Persona persona = session.getPersona();
        Synthesizer synthesizer = persona != null
                ? persona.getSynthesizer()
                : SynthesizerFactory.create(session, ttsFactory.getDefaultTtsService(), player, null);
        String text = notice.pick();
        log.info("播放故障提示 - SessionId: {}, 类别: {}, 内容: {}", session.getSessionId(), notice, text);
        session.transitionTo(DeviceState.SPEAKING);
        synthesizer.synthesize(text);
    }
}
