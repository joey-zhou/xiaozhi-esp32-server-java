package com.xiaozhi.server.web.chat.convert;

import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.resp.ChatTokenResp;
import com.xiaozhi.server.web.chat.WebChatAppService;
import org.mapstruct.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper(componentModel = "spring")
public interface WebChatConvert {

    ChatTokenResp toResp(ChatToken token);

    /**
     * 一轮 Web 对话落库用的 user、assistant 两条消息：裸文本、来源 web、归属 web:<userId>。
     * 元数据由 Conversation 投影层按需拼前缀，库里保持干净。
     */
    default List<MessageBO> toMessages(String sessionId, Integer userId, Integer roleId,
                                       String userText, LocalDateTime userCreatedAt,
                                       String assistantText, LocalDateTime assistantCreatedAt) {
        return List.of(
            toMessage(sessionId, userId, roleId, MessageBO.SENDER_USER, userText, userCreatedAt),
            toMessage(sessionId, userId, roleId, MessageBO.SENDER_ASSISTANT, assistantText, assistantCreatedAt));
    }

    private static MessageBO toMessage(String sessionId, Integer userId, Integer roleId, String sender,
                                       String content, LocalDateTime createTime) {
        MessageBO message = new MessageBO();
        message.setUserId(userId);
        message.setDeviceId(WebChatAppService.ownerId(userId));
        message.setSessionId(sessionId);
        message.setSource(MessageBO.SOURCE_WEB);
        message.setSender(sender);
        message.setMessage(content);
        message.setRoleId(roleId);
        message.setMessageType(MessageBO.MESSAGE_TYPE_NORMAL);
        message.setCreateTime(createTime);
        return message;
    }
}
