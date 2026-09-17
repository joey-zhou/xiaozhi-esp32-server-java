package com.xiaozhi.server.web.chat;

import com.xiaozhi.common.SerialTaskRegistry;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.service.TextChatService;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.role.service.RoleService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
/**
 * Web 聊天会话编排：会话的开启、续接、归属校验、空闲回收与一轮对话的落库。
 * 对话窗口与模型流式调用归 {@link TextChatService}。
 */
@Slf4j
@Service
public class WebChatAppService {

    @Resource
    private RoleService roleService;
    @Resource
    private MessageService messageService;
    @Resource
    private TextChatService textChatService;

    /**
     * 会话空闲多久后回收。浏览器崩溃、断网、进程被杀时收不到 /chat/close，只能靠空闲回收兜底。
     */
    @Value("${web-chat.session-idle-timeout-minutes:30}")
    private long sessionIdleTimeoutMinutes;

    /**
     * sessionId → 进程内会话状态
     */
    private final ConcurrentHashMap<String, WebChatSession> sessions = new ConcurrentHashMap<>();

    /**
     * 一个 Web 聊天会话的进程内状态。
     * lastAccessMillis 每次取用时刷新，空闲回收只看它。
     * 这里只记 roleId 不缓存模型：模型实例的缓存与失效都归模型工厂，
     * 会话再存一份就没有人能在配置变更后把它换掉。
     */
    private static final class WebChatSession {
        private final Conversation conversation;
        private final Integer userId;
        private final Integer roleId;
        private volatile long lastAccessMillis;

        private WebChatSession(Conversation conversation, Integer userId, Integer roleId) {
            this.conversation = conversation;
            this.userId = userId;
            this.roleId = roleId;
            this.lastAccessMillis = System.currentTimeMillis();
        }

        private void touch() {
            this.lastAccessMillis = System.currentTimeMillis();
        }
    }

    /**
     * 开启一个 Web 聊天会话。
     * 当 {@code resumeSessionId} 为空时创建新会话；非空时尝试续接已有会话。
     * 续接时会校验归属（userId一致 且 source='web'），防止误用设备会话或跨用户访问。
     *
     * @param userId           当前登录用户ID
     * @param roleId           角色ID
     * @param resumeSessionId  续接的会话 ID，可为 null
     * @return sessionId
     */
    public String openSession(Integer userId, Integer roleId, String resumeSessionId) {
        RoleBO role = roleService.getBO(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: " + roleId);
        }

        String sessionId;
        if (StringUtils.hasText(resumeSessionId)) {
            assertSessionOwnedByUser(resumeSessionId, userId);
            sessionId = resumeSessionId;
        } else {
            sessionId = UUID.randomUUID().toString();
        }

        Conversation conversation = textChatService.openConversation(ownerId(userId), userId, role, sessionId);
        sessions.put(sessionId, new WebChatSession(conversation, userId, role.getRoleId()));

        log.info("Web 聊天会话已创建: sessionId={}, userId={}, roleId={}, resume={}",
                sessionId, userId, roleId, StringUtils.hasText(resumeSessionId));
        return sessionId;
    }

    /**
     * 创建新会话的便捷重载。
     */
    public String openSession(Integer userId, Integer roleId) {
        return openSession(userId, roleId, null);
    }

    private static String ownerId(Integer userId) {
        return "web:" + userId;
    }

    /**
     * 校验待续接的 sessionId 归属于当前用户的 Web 会话。
     * 存在不匹配时抛出 IllegalArgumentException。
     */
    private void assertSessionOwnedByUser(String sessionId, Integer userId) {
        List<MessageBO> recent = messageService.listHistory(sessionId, 1);
        if (recent.isEmpty()) {
            throw new IllegalArgumentException("会话不存在或已清除: " + sessionId);
        }
        MessageBO first = recent.get(0);
        if (!MessageBO.SOURCE_WEB.equals(first.getSource())) {
            throw new IllegalArgumentException("仅支持续接 Web 来源的会话: " + sessionId);
        }
        if (!userId.equals(first.getUserId())) {
            throw new IllegalArgumentException("会话不属于当前用户: " + sessionId);
        }
    }

    /**
     * 流式聊天：接收用户文本，返回 AI 回复的 ChatToken 流（包含思考过程和正式回复），
     * 并在一轮完整结束时持久化 user/assistant 两条消息。
     *
     * @param sessionId 会话 ID
     * @param text      用户输入文本
     * @param userId    当前登录用户 ID，须与会话创建者一致，防止跨用户会话劫持
     * @return ChatToken 流，前端可根据 type 区分 thinking/content/error
     */
    public Flux<ChatToken> chatStream(String sessionId, String text, Integer userId) {
        WebChatSession session = sessions.get(sessionId);
        if (session == null) {
            return Flux.error(new IllegalStateException("会话不存在或已过期: " + sessionId));
        }
        if (!Objects.equals(session.userId, userId)) {
            return Flux.error(new UnauthorizedException("会话不属于当前用户: " + sessionId));
        }
        session.touch();

        // 每轮重新取角色：角色改了模型/温度、或配置改了 apiKey，下一轮就生效
        RoleBO role = roleService.getBO(session.roleId);
        if (role == null) {
            return Flux.error(new IllegalArgumentException("角色不存在: " + session.roleId));
        }

        LocalDateTime userCreatedAt = LocalDateTime.now();
        return textChatService.streamTurn(session.conversation, role, text, userCreatedAt, reply -> {
            // 持久化裸文本（元数据由 Conversation 投影层按需拼前缀，DB 保持干净）
            LocalDateTime assistantCreatedAt = LocalDateTime.now();
            SerialTaskRegistry.submit(sessionId,
                    () -> persistTurn(sessionId, session, text, userCreatedAt, reply, assistantCreatedAt));
        });
    }

    /**
     * 将一轮 Web 对话的 user + assistant 两条消息写入数据库（source='web'）。
     * 阻塞 JDBC，只能由 {@link SerialTaskRegistry} 的虚拟线程执行，不得在 Reactor 事件循环线程上调用。
     */
    private void persistTurn(String sessionId, WebChatSession session, String userText, LocalDateTime userCreatedAt,
                             String assistantText, LocalDateTime assistantCreatedAt) {
        try {
            MessageBO userBO = buildMessageBO(sessionId, session, MessageBO.SENDER_USER, userText, userCreatedAt);
            MessageBO assistantBO = buildMessageBO(sessionId, session, MessageBO.SENDER_ASSISTANT, assistantText, assistantCreatedAt);
            messageService.saveAll(List.of(userBO, assistantBO));
        } catch (Exception e) {
            log.error("Web 聊天消息持久化失败: sessionId={}", sessionId, e);
        }
    }

    private MessageBO buildMessageBO(String sessionId, WebChatSession session, String sender, String content,
                                     LocalDateTime createTime) {
        MessageBO bo = new MessageBO();
        bo.setUserId(session.userId);
        bo.setDeviceId(ownerId(session.userId));
        bo.setSessionId(sessionId);
        bo.setSource(MessageBO.SOURCE_WEB);
        bo.setSender(sender);
        bo.setMessage(content);
        bo.setRoleId(session.roleId);
        bo.setMessageType(MessageBO.MESSAGE_TYPE_NORMAL);
        bo.setCreateTime(createTime);
        return bo;
    }

    /**
     * 关闭 Web 聊天会话，释放资源。
     * userId 须与会话创建者一致，防止跨用户关闭他人会话（拒绝服务）。
     */
    public void closeSession(String sessionId, Integer userId) {
        WebChatSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        if (!Objects.equals(session.userId, userId)) {
            throw new UnauthorizedException("会话不属于当前用户: " + sessionId);
        }
        sessions.remove(sessionId);
        log.info("Web 聊天会话已关闭: sessionId={}", sessionId);
    }

    /**
     * 检查会话是否存在
     */
    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * 回收空闲超时的会话。会话状态只在进程内，删掉不影响已落库的历史消息，
     * 前端再发消息时会收到「会话不存在或已过期」并重新 open。
     */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void evictIdleSessions() {
        long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(sessionIdleTimeoutMinutes);
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().lastAccessMillis > cutoff) {
                return false;
            }
            log.info("Web 聊天会话空闲超时已回收: sessionId={}", entry.getKey());
            return true;
        });
    }
}
