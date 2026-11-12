package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.SynthesizerFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 出故障时给用户一句话。识别、模型坏掉时设备完全不吭声，用户只会觉得「音箱坏了」。
 * <p>
 * 其他类只上报事实或来拉一句话；故障类别、连续失败后的升级、限流、此刻能不能说、用谁的声音都在这里决定。
 * 提示语不是回复：不进历史、不算已播句子、不录音；设备状态由播放器在真正开播时切换
 */
@Slf4j
@Component
public class FaultNotifier {

    /** 同一会话两次故障提示的最短间隔 */
    static final Duration NOTICE_INTERVAL = Duration.ofSeconds(30);

    @Resource
    private TtsServiceFactory ttsFactory;

    /** 会话起不来（没有可用的对话模型等），说什么都不会有回应。返回这次是否会说 */
    public boolean onSessionUnavailable(ChatSession session) {
        return notice(session, FaultNotice.UNAVAILABLE);
    }

    /** 识别服务没配好或本地模型没就位，每一句都会落到这里 */
    public boolean onSttUnavailable(ChatSession session) {
        return notice(session, FaultNotice.CANNOT_HEAR);
    }

    /** 这一句识别失败：偶尔一次请用户重说；接连失败说明不是偶然，再让重说只会越说越烦 */
    public boolean onSttFailed(ChatSession session) {
        int failures = session.getDialogueContext().recordSttFailure();
        return notice(session, failures >= 2 ? FaultNotice.CANNOT_HEAR : FaultNotice.MISHEARD);
    }

    public void onSttSucceeded(ChatSession session) {
        session.getDialogueContext().recordSttSuccess();
    }

    /** 听清了但处理这一句时出错 */
    public boolean onDialogueFailed(ChatSession session) {
        return notice(session, FaultNotice.DISTRACTED);
    }

    /**
     * 模型一个字都没回就失败了。话由 Persona 在本轮里说，跟着本轮的打断一起作废
     *
     * @return 这次不该说时为空
     */
    public Optional<String> replyFailed(ChatSession session) {
        return claim(session) ? Optional.of(FaultNotice.DISTRACTED.pick()) : Optional.empty();
    }

    /** 有角色就用角色的声音；会话没建起来（没有 Persona）时用默认音色 */
    private boolean notice(ChatSession session, FaultNotice notice) {
        if (!claim(session)) {
            return false;
        }
        String text = notice.pick();
        log.info("播放故障提示 - SessionId: {}, 类别: {}, 内容: {}", session.getSessionId(), notice, text);
        Persona persona = session.getPersona();
        if (persona != null) {
            persona.say(text);
        } else {
            SynthesizerFactory.create(session, ttsFactory.getDefaultTtsService(), session.getPlayer(), null)
                    .synthesize(text);
        }
        return true;
    }

    /** 连接已断、没有播放器、设备正在说别的话、或刚播过一句时不说 */
    private static boolean claim(ChatSession session) {
        if (!session.isAudioChannelOpen()) {
            return false;
        }
        Player player = session.getPlayer();
        if (player == null) {
            log.warn("会话没有播放器，故障提示无法播放 - SessionId: {}", session.getSessionId());
            return false;
        }
        if (player.hasContent()) {
            return false;
        }
        return session.getDialogueContext().claimFaultNotice(NOTICE_INTERVAL);
    }
}
