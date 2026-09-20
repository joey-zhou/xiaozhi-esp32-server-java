package com.xiaozhi.server.web.chat;

import com.xiaozhi.common.SerialTaskRegistry;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.service.TextChatService;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.bo.ConversationBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.message.service.ConversationService;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.server.web.chat.convert.WebChatConvert;
import com.xiaozhi.utils.DateUtils;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
    private ConversationService conversationService;
    @Resource
    private TextChatService textChatService;
    @Resource
    private WebChatConvert webChatConvert;

    /**
     * 会话空闲多久后回收。浏览器崩溃、断网、进程被杀时收不到 /chat/close，只能靠空闲回收兜底。
     */
    @Value("${web-chat.session-idle-timeout-minutes:30}")
    private long sessionIdleTimeoutMinutes;

    /**
     * sessionId → 进程内会话状态。
     * 只是缓存：会话本体是 sys_conversation 里的那一行，重启、空闲回收后拿同一个 sessionId 再聊会按库重建，
     * 前端不需要重新 open。
     */
    private final ConcurrentHashMap<String, WebChatSession> sessions = new ConcurrentHashMap<>();

    /**
     * 一个 Web 聊天会话的进程内状态。
     * lastAccessMillis 每次取用时刷新，空闲回收只看它。
     * 这里只记 roleId 不缓存模型：模型实例的缓存与失效都归模型工厂，
     * 会话再存一份就没有人能在配置变更后把它换掉。
     * saved 表示会话表里已经有这条会话，新会话在第一轮对话时才建档。
     */
    private static final class WebChatSession {
        private final Conversation conversation;
        private final Integer userId;
        private final Integer roleId;
        private volatile boolean saved;
        private volatile long lastAccessMillis;

        private WebChatSession(Conversation conversation, Integer userId, Integer roleId, boolean saved) {
            this.conversation = conversation;
            this.userId = userId;
            this.roleId = roleId;
            this.saved = saved;
            this.lastAccessMillis = DateUtils.millis();
        }

        private void touch() {
            this.lastAccessMillis = DateUtils.millis();
        }
    }

    /**
     * 开启一个 Web 聊天会话。
     * 当 {@code resumeSessionId} 为空时创建新会话；非空时尝试续接已有会话，并按会话表校验归属。
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

        boolean resume = StringUtils.hasText(resumeSessionId);
        String sessionId;
        if (resume) {
            // 另一个标签页已经开着这个会话时直接复用：再建一份会让两个 Conversation 各压缩一遍同一段历史
            WebChatSession opened = sessions.get(resumeSessionId);
            if (opened != null && Objects.equals(opened.userId, userId)) {
                opened.touch();
                return resumeSessionId;
            }
            assertSessionOwnedByUser(resumeSessionId, userId);
            sessionId = resumeSessionId;
        } else {
            sessionId = UUID.randomUUID().toString();
        }

        Conversation conversation = textChatService.openConversation(ownerId(userId), userId, role, sessionId);
        WebChatSession opened = sessions.putIfAbsent(sessionId,
                new WebChatSession(conversation, userId, role.getRoleId(), resume));
        if (opened != null) {
            // 并发续接时保留先放进去的那份，落选的还没有新消息，直接丢
            return sessionId;
        }

        log.info("Web 聊天会话已创建: sessionId={}, userId={}, roleId={}, resume={}",
                sessionId, userId, roleId, resume);
        return sessionId;
    }

    /**
     * 创建新会话的便捷重载。
     */
    public String openSession(Integer userId, Integer roleId) {
        return openSession(userId, roleId, null);
    }

    /** Web 聊天在记忆与消息表里的归属标识，不与任何真实设备共享记忆。 */
    public static String ownerId(Integer userId) {
        return "web:" + userId;
    }

    /**
     * 校验待续接的 sessionId 是当前用户的 Web 会话。
     * 存在不匹配时抛出 IllegalArgumentException。
     */
    private void assertSessionOwnedByUser(String sessionId, Integer userId) {
        ConversationBO conversation = conversationService.get(sessionId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在或已清除: " + sessionId);
        }
        if (!userId.equals(conversation.getUserId())) {
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
        WebChatSession session;
        try {
            session = resolveSession(sessionId, userId);
        } catch (RuntimeException e) {
            return Flux.error(e);
        }
        session.touch();

        // 每轮重新取角色：角色改了模型/温度、或配置改了 apiKey，下一轮就生效
        RoleBO role = roleService.getBO(session.roleId);
        if (role == null) {
            return Flux.error(new IllegalArgumentException("角色不存在: " + session.roleId));
        }

        // 回复还没开始就建档，前端这一轮结束刷新列表时新会话已经在里面。
        // 建档、落消息、撤档都排在同一条串行队列上，首轮失败后紧接着重发也不会乱序
        boolean firstTurn = !session.saved;
        if (firstTurn) {
            session.saved = true;
            SerialTaskRegistry.submit(sessionId, () -> createConversation(sessionId, userId, session, text));
        }

        LocalDateTime userCreatedAt = DateUtils.now();
        AtomicBoolean turnCompleted = new AtomicBoolean();
        return textChatService.streamTurn(session.conversation, role, text, userCreatedAt, (reply, assistantCreatedAt) -> {
            turnCompleted.set(true);
            SerialTaskRegistry.submit(sessionId,
                    () -> persistTurn(sessionId, session, text, userCreatedAt, reply, assistantCreatedAt));
        }).doFinally(signal -> {
            // 首轮没聊成（模型报错、客户端中断、空回复）就撤掉刚建的档，否则侧栏留下一个打开是空的会话；下一轮重新建档
            if (firstTurn && !turnCompleted.get()) {
                session.saved = false;
                SerialTaskRegistry.submit(sessionId, () -> discardEmptyConversation(sessionId, userId));
            }
        });
    }

    private void createConversation(String sessionId, Integer userId, WebChatSession session, String firstMessage) {
        try {
            conversationService.create(sessionId, userId, session.roleId, firstMessage);
        } catch (RuntimeException e) {
            // 建档失败下一轮再试，不能让消息落在一个列表里看不到的会话上
            session.saved = false;
            log.error("Web 聊天会话建档失败: sessionId={}", sessionId, e);
        }
    }

    private void discardEmptyConversation(String sessionId, Integer userId) {
        try {
            conversationService.delete(userId, List.of(sessionId));
        } catch (RuntimeException e) {
            log.error("撤掉空会话失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 取进程内会话，没有就按会话表重建。
     * 只有还没落库的新会话（open 后一句没说就被回收或重启）才会真的找不到，这时无从得知角色，只能报错。
     */
    private WebChatSession resolveSession(String sessionId, Integer userId) {
        WebChatSession cached = sessions.get(sessionId);
        if (cached != null) {
            if (!Objects.equals(cached.userId, userId)) {
                throw new UnauthorizedException("会话不属于当前用户: " + sessionId);
            }
            return cached;
        }
        ConversationBO saved = conversationService.get(sessionId);
        if (saved == null) {
            throw new IllegalArgumentException("会话不存在或已删除: " + sessionId);
        }
        if (!userId.equals(saved.getUserId())) {
            throw new UnauthorizedException("会话不属于当前用户: " + sessionId);
        }
        RoleBO role = roleService.getBO(saved.getRoleId());
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: " + saved.getRoleId());
        }
        // 重建要查库、加载历史，放在 map 的锁外做；并发重建时保留先放进去的那份，落选的还没有新消息，直接丢
        WebChatSession rebuilt = new WebChatSession(
                textChatService.openConversation(ownerId(userId), userId, role, sessionId), userId, role.getRoleId(), true);
        WebChatSession winner = sessions.putIfAbsent(sessionId, rebuilt);
        if (winner == null) {
            log.info("Web 聊天会话已按会话表重建: sessionId={}, userId={}", sessionId, userId);
            return rebuilt;
        }
        return winner;
    }

    /**
     * 将一轮 Web 对话的 user + assistant 两条消息写入数据库，并刷新会话的最后对话时间。
     * 阻塞 JDBC，只能由 {@link SerialTaskRegistry} 的虚拟线程执行，不得在 Reactor 事件循环线程上调用。
     */
    private void persistTurn(String sessionId, WebChatSession session, String userText, LocalDateTime userCreatedAt,
                             String assistantText, LocalDateTime assistantCreatedAt) {
        try {
            messageService.saveAll(webChatConvert.toMessages(sessionId, session.userId, session.roleId,
                    userText, userCreatedAt, assistantText, assistantCreatedAt));
            conversationService.touch(sessionId);
        } catch (Exception e) {
            log.error("Web 聊天消息持久化失败: sessionId={}", sessionId, e);
        }
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
        // 剩下的对话压成摘要，否则没触发过压缩的短会话永远进不了摘要
        session.conversation.flush();
        log.info("Web 聊天会话已关闭: sessionId={}", sessionId);
    }

    /**
     * 删除当前用户的会话。进程里还开着的会话直接移除、不再压缩，免得给已删除的会话留下新摘要。
     *
     * @return 实际删除的会话数，不归属当前用户的会话跳过
     */
    public int deleteConversations(Integer userId, List<String> sessionIds) {
        for (String sessionId : sessionIds) {
            sessions.computeIfPresent(sessionId, (id, session) -> {
                if (!Objects.equals(session.userId, userId)) {
                    return session;
                }
                session.conversation.discard();
                return null;
            });
        }
        List<String> deleted = conversationService.delete(userId, sessionIds);
        log.info("Web 聊天会话已删除: userId={}, sessionIds={}", userId, deleted);
        return deleted.size();
    }

    /**
     * 检查会话是否存在
     */
    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * 回收空闲超时的会话。会话状态只在进程内，删掉不影响已落库的历史消息，
     * 同一个 sessionId 再来消息时 {@link #resolveSession} 会按会话表重建。
     */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void evictIdleSessions() {
        long cutoff = DateUtils.millis() - TimeUnit.MINUTES.toMillis(sessionIdleTimeoutMinutes);
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().lastAccessMillis > cutoff) {
                return false;
            }
            entry.getValue().conversation.flush();
            log.info("Web 聊天会话空闲超时已回收: sessionId={}", entry.getKey());
            return true;
        });
    }
}
