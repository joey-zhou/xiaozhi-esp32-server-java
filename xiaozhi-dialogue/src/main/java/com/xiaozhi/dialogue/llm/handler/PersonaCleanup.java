package com.xiaozhi.dialogue.llm.handler;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.Synthesizer;
import com.xiaozhi.dialogue.runtime.Persona;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 会话终止时清理 Persona 相关资源（上游合成订阅、播放器），并把这段对话剩下的内容压进摘要。
 */
@Slf4j
@Component
public class PersonaCleanup {

    /**
     * 由 {@code SessionManager.closeSession} 在会话被摘出注册表之前直接调用。
     * 不挂在 ChatSessionClosedEvent 上：WebSocket 硬断线时连接已关，事件根本发不出去；
     * 服务端主动关的路径上事件虽然发得出去，但会话已被摘除，靠 sessionId 回查只能拿到 null。
     */
    public void cleanup(ChatSession session) {
        if (session == null) {
            return;
        }
        Persona persona = session.getPersona();
        // 先断上游再停播放器，与打断路径同序，否则清完队列后新帧还会继续入队
        if (persona != null) {
            Synthesizer synthesizer = persona.getSynthesizer();
            if (synthesizer != null) {
                try {
                    synthesizer.cancel();
                } catch (Exception e) {
                    log.warn("取消语音合成失败 - SessionId: {}: {}", session.getSessionId(), e.getMessage());
                }
            }
        }
        Player player = session.getPlayer();
        if (player != null) {
            try {
                player.stop();
            } catch (Exception e) {
                log.warn("停止播放器失败 - SessionId: {}: {}", session.getSessionId(), e.getMessage());
            }
        }
        if (persona != null) {
            // 设备断开就是这段对话的结束：没到上限的剩余轮次在这里进摘要，否则短对话永远进不了摘要
            persona.getConversation().flush();
        }
    }
}
