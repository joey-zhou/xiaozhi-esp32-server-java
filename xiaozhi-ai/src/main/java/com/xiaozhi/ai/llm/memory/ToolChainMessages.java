package com.xiaozhi.ai.llm.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaozhi.common.model.bo.MessageBO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 一条工具调用链落库成两条消息：模型的调用请求（sender=assistant、TOOL_CALL）与工具结果（sender=tool、TOOL_RESPONSE）。
 * 设备对话与网页聊天共用，读回在 {@link DatabaseChatMemory}
 */
@Slf4j
public final class ToolChainMessages {

    private ToolChainMessages() {
    }

    /**
     * @param base       每次返回一条新消息，已填好归属（用户、owner、会话、来源、角色）
     * @param createTime 两条消息的时间
     */
    public static List<MessageBO> toMessages(ToolChainPair chain, Supplier<MessageBO> base, LocalDateTime createTime) {
        MessageBO call = base.get();
        call.setSender(MessageBO.SENDER_ASSISTANT);
        call.setMessage(chain.toolCallMessage().getText());
        call.setMessageType(MessageBO.MESSAGE_TYPE_TOOL_CALL);
        call.setCreateTime(createTime);
        try {
            call.setToolCalls(ToolCallMessageCodec.encodeToolCalls(chain.toolCallMessage().getToolCalls()));
        } catch (JsonProcessingException e) {
            log.warn("序列化 tool call 请求失败", e);
        }

        ToolResponseMessage toolResponse = chain.toolResponseMessage();
        MessageBO response = base.get();
        response.setSender(MessageBO.SENDER_TOOL);
        response.setMessage(toolResponse.getResponses().stream()
                .map(ToolResponseMessage.ToolResponse::responseData)
                .collect(Collectors.joining("\n")));
        response.setMessageType(MessageBO.MESSAGE_TYPE_TOOL_RESPONSE);
        response.setCreateTime(createTime);
        try {
            response.setToolCalls(ToolCallMessageCodec.encodeToolResponses(toolResponse.getResponses()));
        } catch (JsonProcessingException e) {
            log.warn("序列化 tool response 信息失败", e);
        }
        return List.of(call, response);
    }
}
