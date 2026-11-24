package com.xiaozhi.server.web.chat.convert;

import com.xiaozhi.ai.llm.memory.ToolChainMessages;
import com.xiaozhi.ai.llm.memory.ToolChainPair;
import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.bo.MessageAttachmentBO;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.bo.MessageMetadataBO;
import com.xiaozhi.common.model.resp.ChatTokenResp;
import com.xiaozhi.server.web.chat.WebChatAppService;
import org.mapstruct.Mapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public interface WebChatConvert {

    ChatTokenResp toResp(ChatToken token);

    /**
     * 一轮网页对话落库的消息：用户消息（附件记在 metadata）、工具调用链（挂用户消息的时间）、助手回复
     */
    default List<MessageBO> toMessages(String sessionId, Integer userId, Integer roleId,
                                       String userText, List<MessageAttachmentBO> attachments, LocalDateTime userCreatedAt,
                                       List<ToolChainPair> toolChains,
                                       String assistantText, LocalDateTime assistantCreatedAt) {
        List<MessageBO> messages = new ArrayList<>();
        MessageBO user = toMessage(sessionId, userId, roleId, MessageBO.SENDER_USER, userText, userCreatedAt);
        if (!attachments.isEmpty()) {
            user.setMetadata(MessageMetadataBO.builder().attachments(attachments).build());
        }
        messages.add(user);
        for (ToolChainPair chain : toolChains) {
            messages.addAll(ToolChainMessages.toMessages(chain, () -> ownedBy(sessionId, userId, roleId), userCreatedAt));
        }
        messages.add(toMessage(sessionId, userId, roleId, MessageBO.SENDER_ASSISTANT, assistantText, assistantCreatedAt));
        return messages;
    }

    private static MessageBO ownedBy(String sessionId, Integer userId, Integer roleId) {
        MessageBO message = new MessageBO();
        message.setUserId(userId);
        message.setDeviceId(WebChatAppService.ownerId(userId));
        message.setSessionId(sessionId);
        message.setSource(MessageBO.SOURCE_WEB);
        message.setRoleId(roleId);
        return message;
    }

    private static MessageBO toMessage(String sessionId, Integer userId, Integer roleId, String sender,
                                       String content, LocalDateTime createTime) {
        MessageBO message = ownedBy(sessionId, userId, roleId);
        message.setSender(sender);
        message.setMessage(content);
        message.setMessageType(MessageBO.MESSAGE_TYPE_NORMAL);
        message.setCreateTime(createTime);
        return message;
    }
}
