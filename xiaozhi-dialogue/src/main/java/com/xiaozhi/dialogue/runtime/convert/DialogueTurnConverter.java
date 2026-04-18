package com.xiaozhi.dialogue.runtime.convert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.memory.ToolCallMessageCodec;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.bo.MessageMetadataBO;
import com.xiaozhi.dialogue.runtime.DialogueContext;
import com.xiaozhi.dialogue.runtime.DialogueTurn;
import com.xiaozhi.dialogue.runtime.ToolChainPair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DialogueTurn 到持久化层 {@link MessageBO} 列表的转换器。
 * <p>
 * 将一轮对话（user + 可选 tool-call / tool-response + assistant）拆分为 2 到 4 条 MessageBO。
 * 职责上与 {@code RoleConverter} / {@code ConfigConverter} 一致：把领域对象转成持久化 / 传输用的 BO。
 */
@Slf4j
@Component
public class DialogueTurnConverter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 将 DialogueTurn 拆分为 MessageBO 列表（供 MessageService 批量持久化） */
    public List<MessageBO> toMessages(DialogueTurn turn) {
        ChatResponse chatResponse = turn.getChatResponse();
        Generation generation = chatResponse.getResult();
        Assert.notNull(generation, "Generation is null from ChatResponse");

        AssistantMessage finalAssistantMessage = generation.getOutput();

        List<MessageBO> messages = new ArrayList<>();

        // 1. UserMessage
        messages.add(toMessageBO(turn, turn.getUserMessage()));

        // 2. 按顺序插入所有工具调用链：每个 pair 拆成 Assistant(toolCall) + Tool(response) 两条
        for (ToolChainPair chain : turn.getToolChains()) {
            if (chain == null || chain.toolCallMessage() == null || chain.toolResponseMessage() == null) {
                continue;
            }
            messages.add(toToolCallAssistantMessageBO(turn, chain.toolCallMessage()));
            messages.add(toToolResponseMessageBO(turn, chain.toolResponseMessage()));
        }

        // 3. 最终 AssistantMessage
        messages.add(toMessageBO(turn, finalAssistantMessage));

        return messages;
    }

    private MessageBO toMessageBO(DialogueTurn turn, AbstractMessage message) {
        Conversation conversation = turn.getConversation();
        MessageBO messageBO = new MessageBO();
        messageBO.setUserId(conversation.getUserId());
        messageBO.setDeviceId(conversation.getOwnerId());
        messageBO.setSessionId(conversation.getSessionId());
        messageBO.setSource(MessageBO.SOURCE_DEVICE);
        messageBO.setSender(message.getMessageType().getValue());
        messageBO.setMessage(message.getText());
        messageBO.setRoleId(conversation.getRoleId());
        messageBO.setMessageType(MessageBO.MESSAGE_TYPE_NORMAL);

        Path userSpeechPath = turn.getUserSpeechPath();
        List<DialogueContext.ToolCallInfo> toolCallDetails = turn.getToolCallDetails();

        switch (message.getMessageType()) {
            case USER:
                if (userSpeechPath != null) {
                    messageBO.setAudioPath(userSpeechPath.toString());
                }
                messageBO.setCreateTime(LocalDateTime.ofInstant(turn.getUserMessageCreatedAt(), ZoneId.systemDefault()));
                // 从 UserMessage.metadata 抽取结构化元数据（speaker/emotion 等）写入 MessageBO.metadata
                if (message instanceof UserMessage userMessage
                        && userMessage.getMetadata() != null
                        && userMessage.getMetadata().get(MessageMetadataBO.METADATA_KEY) instanceof MessageMetadataBO metadata) {
                    messageBO.setMetadata(metadata);
                }
                break;
            case ASSISTANT:
                messageBO.setCreateTime(LocalDateTime.ofInstant(turn.getAssistantMessageCreatedAt(), ZoneId.systemDefault()));
                if (!toolCallDetails.isEmpty()) {
                    try {
                        messageBO.setToolCalls(OBJECT_MAPPER.writeValueAsString(toolCallDetails));
                    } catch (JsonProcessingException e) {
                        log.warn("序列化工具调用详情失败", e);
                    }
                }
                break;
            default:
                break;
        }

        return messageBO;
    }

    /** 构建工具调用请求的 MessageBO（sender=assistant, messageType=TOOL_CALL） */
    private MessageBO toToolCallAssistantMessageBO(DialogueTurn turn, AssistantMessage toolCallAssistantMessage) {
        Conversation conversation = turn.getConversation();

        MessageBO messageBO = new MessageBO();
        messageBO.setUserId(conversation.getUserId());
        messageBO.setDeviceId(conversation.getOwnerId());
        messageBO.setSessionId(conversation.getSessionId());
        messageBO.setSource(MessageBO.SOURCE_DEVICE);
        messageBO.setSender(MessageBO.SENDER_ASSISTANT);
        messageBO.setMessage(toolCallAssistantMessage.getText());
        messageBO.setRoleId(conversation.getRoleId());
        messageBO.setMessageType(MessageBO.MESSAGE_TYPE_TOOL_CALL);
        messageBO.setCreateTime(LocalDateTime.ofInstant(turn.getAssistantMessageCreatedAt(), ZoneId.systemDefault()));
        try {
            messageBO.setToolCalls(ToolCallMessageCodec.encodeToolCalls(toolCallAssistantMessage.getToolCalls()));
        } catch (JsonProcessingException e) {
            log.warn("序列化 tool call 请求失败", e);
        }
        return messageBO;
    }

    /** 构建工具执行结果的 MessageBO（sender=tool, messageType=TOOL_RESPONSE） */
    private MessageBO toToolResponseMessageBO(DialogueTurn turn, ToolResponseMessage toolResponseMessage) {
        Conversation conversation = turn.getConversation();

        MessageBO messageBO = new MessageBO();
        messageBO.setUserId(conversation.getUserId());
        messageBO.setDeviceId(conversation.getOwnerId());
        messageBO.setSessionId(conversation.getSessionId());
        messageBO.setSource(MessageBO.SOURCE_DEVICE);
        messageBO.setSender(MessageBO.SENDER_TOOL);
        String responseText = toolResponseMessage.getResponses().stream()
                .map(ToolResponseMessage.ToolResponse::responseData)
                .collect(Collectors.joining("\n"));
        messageBO.setMessage(responseText);
        messageBO.setRoleId(conversation.getRoleId());
        messageBO.setMessageType(MessageBO.MESSAGE_TYPE_TOOL_RESPONSE);
        messageBO.setCreateTime(LocalDateTime.ofInstant(turn.getAssistantMessageCreatedAt(), ZoneId.systemDefault()));
        try {
            messageBO.setToolCalls(ToolCallMessageCodec.encodeToolResponses(toolResponseMessage.getResponses()));
        } catch (JsonProcessingException e) {
            log.warn("序列化 tool response 信息失败", e);
        }
        return messageBO;
    }
}
