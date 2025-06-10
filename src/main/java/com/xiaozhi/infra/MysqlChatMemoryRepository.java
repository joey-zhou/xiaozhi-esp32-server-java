package com.xiaozhi.infra;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dao.MessageMapper;
import com.xiaozhi.entity.Base;
import com.xiaozhi.entity.SysMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Repository;

import java.util.*;

@Slf4j
@Repository
public class MysqlChatMemoryRepository implements ChatMemoryRepository {


    @Resource
    private MessageMapper messageMapper;

    @Override
    public List<String> findConversationIds() {
        return List.of();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        log.info("findByConversationId");
        try {
            SysMessage queryMessage = new SysMessage();
            queryMessage.setSessionId(conversationId);
            PageFilter pageFilter = new PageFilter(1, 10);
            if(pageFilter != null){
                PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
            }
            List<SysMessage> messages = messageMapper.query(queryMessage);
            messages.sort(Comparator.comparing(Base::getCreateTime));
            List<Message> result = new ArrayList<>();
            for(SysMessage message : messages){
                if(message.getMessageType().equals(MessageType.USER.getValue())){
                    Map<String, Object> properties = new HashMap<>();
                    properties.put("deviceId", message.getDeviceId());
                    properties.put("roleId", message.getRoleId());
                    properties.put("messageId", message.getMessageId());
                    UserMessage message1 = UserMessage.builder().text(message.getMessage()).metadata(properties).build();
                    result.add(message1);
                }else{
                    Map<String, Object> properties = new HashMap<>();
                    properties.put("deviceId", message.getDeviceId());
                    properties.put("roleId", message.getRoleId());
                    properties.put("messageId", message.getMessageId());
                    AssistantMessage message1 = new AssistantMessage(message.getMessage(),properties);
                    result.add(message1);
                }

            }
            return result;
        } catch (Exception e) {
            log.error("获取历史消息时出错: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        log.info("saveAll");
        for(Message message : messages) {
            Map<String, Object> map = message.getMetadata();
            if(map.get("messageId") == null){
                SysMessage saveMessage = new SysMessage();
                saveMessage.setSessionId(conversationId);
                saveMessage.setMessage(message.getText());
                saveMessage.setMessageType(map.get("messageType").toString());
                saveMessage.setDeviceId(map.get("deviceId") == null? null:map.get("deviceId").toString());
                saveMessage.setRoleId(map.get("roleId") == null? null:(Integer)map.get("roleId"));
                saveMessage.setSender(message.getMessageType().getValue());
                saveMessage.setCreateTime(new Date());
                messageMapper.add(saveMessage);
            }
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        log.info("deleteByConversationId");
        SysMessage saveMessage = new SysMessage();
        saveMessage.setSessionId(conversationId);
        messageMapper.delete(saveMessage);
    }
}
