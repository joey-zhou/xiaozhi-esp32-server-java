package com.xiaozhi.dialogue.llm.handler;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.playback.Player;
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
        if (persona == null) {
            stopPlayer(session);
            return;
        }
        // 说到一半断开也按打断收尾：用户听到的部分进历史
        persona.interrupt();
        // 设备断开就是这段对话的结束：没到上限的剩余轮次在这里进摘要，否则短对话永远进不了摘要
        persona.getConversation().flush();
    }

    /** 会话还没建起 Persona 时（如故障提示用默认音色播），播放器是单独建的 */
    private void stopPlayer(ChatSession session) {
        Player player = session.getPlayer();
        if (player != null) {
            try {
                player.stop();
            } catch (Exception e) {
                log.warn("停止播放器失败 - SessionId: {}: {}", session.getSessionId(), e.getMessage());
            }
        }
    }
}
