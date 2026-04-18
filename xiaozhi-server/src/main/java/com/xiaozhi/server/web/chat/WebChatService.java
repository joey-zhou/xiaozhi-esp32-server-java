package com.xiaozhi.server.web.chat;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.ai.llm.memory.ChatMemory;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.memory.ConversationContext;
import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;
import com.xiaozhi.ai.llm.memory.MessageWindowConversation;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.role.service.RoleService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
/**
 * Web 聊天服务：为纯文本 Web 客户端提供流式 AI 对话能力。
 * 轻量级实现，不涉及 STT/TTS/Player 等音频组件
 */
@Slf4j
@Service
public class WebChatService {
    @Resource
    private ChatModelFactory chatModelFactory;
    @Resource
    private RoleService roleService;
    @Resource
    private ChatMemory chatMemory;
    @Resource
    private MessageService messageService;

    @Value("${conversation.max-messages:16}")
    private int maxMessages;

    /**
     * sessionId → Conversation 映射
     */
    private final ConcurrentHashMap<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChatModel> chatModels = new ConcurrentHashMap<>();

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
        String ownerId = "web:" + userId;

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

        // 初始化 Conversation：Web 场景始终按 sessionId 加载（新会话为空，续接会拉到历史）。
        Conversation conversation = MessageWindowConversation.builder()
                .chatMemory(chatMemory)
                .maxMessages(maxMessages)
                .ownerId(ownerId)
                .roleId(role.getRoleId())
                .roleDesc(role.getRoleDesc())
                .userId(userId)
                .sessionId(sessionId)
                .sessionScoped(true)
                .build();
        conversations.put(sessionId, conversation);

        // 初始化 ChatModel
        ChatModel chatModel = chatModelFactory.getChatModel(role);
        chatModels.put(sessionId, chatModel);

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
     * 流式聊天：接收用户文本，返回 AI 回复的文本流，并在完成时持久化 user/assistant 两条消息。
     *
     * @param sessionId 会话 ID
     * @param text      用户输入文本
     * @return AI 回复文本流
     */
    public Flux<String> chatStream(String sessionId, String text) {
        Conversation conversation = conversations.get(sessionId);
        ChatModel chatModel = chatModels.get(sessionId);
        if (conversation == null || chatModel == null) {
            return Flux.error(new IllegalStateException("会话不存在或已过期: " + sessionId));
        }

        // Web 场景：裸文本 UserMessage + 时间戳 metadata；
        // Conversation 投影层会在送 LLM 前拼出 [时间戳] 文本 的前缀。
        // 无 speaker/emotion，故不挂 MessageMetadataBO。
        LocalDateTime userCreatedAt = LocalDateTime.now();
        Instant userInstant = userCreatedAt.atZone(ZoneId.systemDefault()).toInstant();
        UserMessage userMessage = new UserMessage(text);
        MessageTimeMetadata.setTimeMillis(userMessage, userInstant);
        conversation.add(userMessage);

        // Web 场景无位置
        List<Message> messages = conversation.messages(ConversationContext.EMPTY);

        Prompt prompt = new Prompt(messages);

        StringBuilder fullResponse = new StringBuilder();

        return chatModel.stream(prompt)
                .mapNotNull(ChatResponse::getResult)
                .mapNotNull(Generation::getOutput)
                .mapNotNull(AssistantMessage::getText)
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    if (fullResponse.isEmpty()) {
                        return;
                    }
                    String reply = fullResponse.toString();
                    conversation.add(new AssistantMessage(reply));
                    // 持久化裸文本（元数据由 Conversation 投影层按需拼前缀，DB 保持干净）
                    persistTurn(conversation, text, userCreatedAt, reply, LocalDateTime.now());
                })
                .doOnError(e -> log.error("Web 聊天流式响应失败: sessionId={}", sessionId, e));
    }

    /**
     * 将一轮 Web 对话的 user + assistant 两条消息写入数据库（source='web'）。
     * 单独提出方便出错时不影响流式完成。
     */
    private void persistTurn(Conversation conversation, String userText, LocalDateTime userCreatedAt,
                             String assistantText, LocalDateTime assistantCreatedAt) {
        try {
            MessageBO userBO = buildMessageBO(conversation, MessageBO.SENDER_USER, userText, userCreatedAt);
            MessageBO assistantBO = buildMessageBO(conversation, MessageBO.SENDER_ASSISTANT, assistantText, assistantCreatedAt);
            messageService.saveAll(List.of(userBO, assistantBO));
        } catch (Exception e) {
            log.error("Web 聊天消息持久化失败: sessionId={}", conversation.sessionId(), e);
        }
    }

    private MessageBO buildMessageBO(Conversation conversation, String sender, String content, LocalDateTime createTime) {
        MessageBO bo = new MessageBO();
        bo.setUserId(conversation.getUserId());
        bo.setDeviceId(conversation.getOwnerId());
        bo.setSessionId(conversation.sessionId());
        bo.setSource(MessageBO.SOURCE_WEB);
        bo.setSender(sender);
        bo.setMessage(content);
        bo.setRoleId(conversation.getRoleId());
        bo.setMessageType(MessageBO.MESSAGE_TYPE_NORMAL);
        bo.setCreateTime(createTime);
        return bo;
    }

    /**
     * 关闭 Web 聊天会话，释放资源
     */
    public void closeSession(String sessionId) {
        conversations.remove(sessionId);
        chatModels.remove(sessionId);
        log.info("Web 聊天会话已关闭: sessionId={}", sessionId);
    }

    /**
     * 检查会话是否存在
     */
    public boolean hasSession(String sessionId) {
        return conversations.containsKey(sessionId);
    }
}
