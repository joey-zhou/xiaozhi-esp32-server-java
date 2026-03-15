package com.xiaozhi.dialogue.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.memory.ConversationIdentifier;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.utils.SpringUtils;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.Assert;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * SysMessage 也可以属于Domain Entity，但具体如何持久化存储是应当由外部具体实现类实现。
 * SysMessage = AbstractMessage + ConversationIdentifier + Usage + AudioPath + Duration + createdAt
 * TODO 这个公式，实际可以将UserMessage+ AssistantMessage 组合为 Dialogue(甚至可以继承于 ObservationContext)
 *  Dialogue对象应该作为方法内局部变量，而非 Persona属性。
 * ConversationIdentifier = deviceId + sessionId + roleId
 * MessageIdentifier = ConversationIdentifier + time-stamp
 */

@Slf4j
@Value
public class Dialogue {
    private static final String TIME_MILLIS_KEY = "TIME_MILLIS";

    // 未来有需要时，再将UserMessage改为Prompt
    private UserMessage userMessage;
    private ChatResponse chatResponse;
    private ConversationIdentifier conversationId;
    private Instant userMessageCreatedAt;
    private Instant assistantMessageCreatedAt;
    private boolean disturbed;
    private List<ChatSession.ToolCallInfo> toolCallDetails;
    private Path userSpeechPath;

    // 暂时不启用
    //private Path assistantSpeechPath;

    private final AssistantMessage assistantMessage;

    // 对应于数据库的ttfs
    private final Duration timeToFirstToken;
    // 对应于数据库的response_time
    //private Duration timeToFirstSpeech;

    @Builder
    public Dialogue(UserMessage userMessage, ChatResponse chatResponse, ConversationIdentifier conversationId, Path userSpeechPath, Instant userMessageCreatedAt, Instant assistantMessageCreatedAt, boolean disturbed, List<ChatSession.ToolCallInfo> toolCallDetails) {
        Assert.notNull(userMessage, "用户消息对象不应该为NULL！");
        Assert.notNull(chatResponse, "大语言模型的响应对象不应该为NULL！");
        Assert.notNull(conversationId, "会话ID对象不应该为NULL！");
        // userSpeechPath 可以为空
        Assert.notNull(userMessageCreatedAt, "用户消息创建时间对象不应该为NULL！");
        Assert.notNull(assistantMessageCreatedAt, "模型响应创建时间对象不应该为NULL！");
        this.userMessage = userMessage;
        this.chatResponse = chatResponse;
        this.conversationId = conversationId;
        this.userSpeechPath = userSpeechPath;
        this.userMessageCreatedAt = userMessageCreatedAt;
        this.assistantMessageCreatedAt = assistantMessageCreatedAt;
        this.disturbed = disturbed;
        this.toolCallDetails = toolCallDetails != null ? toolCallDetails : List.of();
        this.assistantMessage = chatResponse.getResult().getOutput();
        this.timeToFirstToken = Duration.between(userMessageCreatedAt, assistantMessageCreatedAt);
    }


    /**
     * 工具方法：将spring-ai的Message转换为数据库记录的SysMessage。
     * 需要考虑 SysMessage 是包含多个时间节点产生的信息的，要有一个ID关联，要能更新。
     * 后续引入spring-data框架。
     *
     * @return
     */
    public List<SysMessage> convert(){
        Generation generation = chatResponse.getResult();
        Assert.notNull(generation, "Generation is null from ChatResponse");

        // 获取最后的完整的AssistantMessage
        AssistantMessage assistantMessage = generation.getOutput();


        return List.of(userMessage, assistantMessage).stream()
                .map(msg -> convert(msg))
                .toList();

    }


    private SysMessage convert(org.springframework.ai.chat.messages.AbstractMessage msg) {
        SysMessage message = new SysMessage();
        message.setDeviceId(conversationId.getDeviceId());
        message.setSessionId(conversationId.getSessionId());
        message.setSender(msg.getMessageType().getValue());
        message.setMessage(msg.getText());
        message.setRoleId(conversationId.getRoleId());
        boolean functionCalled = !toolCallDetails.isEmpty();
        message.setMessageType(functionCalled ? SysMessage.MESSAGE_TYPE_FUNCTION_CALL : SysMessage.MESSAGE_TYPE_NORMAL);

        switch (msg.getMessageType()) {
            case USER:
                // TODO 这个时间戳 转换后丢失精度容易出现错误。
                Instant instant = userMessageCreatedAt.truncatedTo(ChronoUnit.SECONDS);
                if(userSpeechPath!=null) {
                    message.setAudioPath(userSpeechPath.toString());
                }
                message.setCreateTime(LocalDateTime.from(instant));
                break;

            case ASSISTANT:
                // 首次模型响应时间、首次TTFS响应时间都是AssistantMessage才具备的metadata，UserMessage没有实际也不应该有。
                message.setCreateTime(LocalDateTime.from(assistantMessageCreatedAt.truncatedTo(ChronoUnit.SECONDS)));
                // 工具调用详情只记录在 assistant 消息上
                if (functionCalled) {
                    try {
//                         ObjectMapper OBJECT_MAPPER = new ObjectMapper();
                        ObjectMapper OBJECT_MAPPER = SpringUtils.getApplicationContext().getBean(ObjectMapper.class);
                        message.setToolCalls(OBJECT_MAPPER.writeValueAsString(toolCallDetails));
                    } catch (JsonProcessingException e) {
                        log.warn("序列化工具调用详情失败", e);
                    }
                }
                break;
            default:
        }

        return message;
    }

    public void injectInstants() {
        setTimeMillis(userMessage, userMessageCreatedAt);
        setTimeMillis(assistantMessage, assistantMessageCreatedAt);
    }

    public static void setTimeMillis(Message message, Instant timeMillis){
        message.getMetadata().put(TIME_MILLIS_KEY, timeMillis);
    }

    public static Instant getTimeMillis(Message message){
        return (Instant) message.getMetadata().getOrDefault(TIME_MILLIS_KEY,System.currentTimeMillis());
    }

}
