package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import org.springframework.ai.chat.messages.*;


import java.util.*;

/**
 * 限定消息条数（消息窗口）的Conversation实现。根据不同的策略，可实现聊天会话的持久化、加载、清除等功能。
 * 短期记忆，只能记住当前对话有限的消息条数（多轮）。
 */
public class MessageWindowConversation extends Conversation {
    private final int maxMessages;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MessageWindowConversation.class);


    public MessageWindowConversation(DeviceBO device, RoleBO role, String sessionId, int maxMessages, ChatMemory chatMemory){
        super(device, role, sessionId);
        this.maxMessages = maxMessages;

        logger.info("加载设备{}的普通消息(MessageBO.MESSAGE_TYPE_NORMAL)作为对话历史", device.getDeviceId());
        List<Message> history = chatMemory.find(device.getDeviceId(), role.getRoleId(), maxMessages);
        super.messages.addAll(history) ;
    }

    public static class Builder {
        private DeviceBO device;
        private RoleBO role;
        private String sessionId;
        private int maxMessages;
        private ChatMemory chatMemory;

        public Builder device(DeviceBO device) {
            this.device = device;
            return this;
        }

        public Builder role(RoleBO role) {
            this.role = role;
            return this;
        }
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder maxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        public MessageWindowConversation build(){
            return new MessageWindowConversation(device,role,sessionId,maxMessages,chatMemory);
        }
    }

    public static Builder builder() {
        return new Builder();
    }


    /**
     * 添加消息
     * 后续考虑：继承封装UserMessage和AssistantMessage,UserMessageWithTime,AssistantMessageWithTime
     * 后续考虑：将function 或者 mcp 的相关信息封装在AssistantMessageWithTime，来精细处理。或者根据元数据判断是function_call还是mcp调用
     * @param message
     */
    @Override
    public synchronized void add(Message message) {

        if(message instanceof UserMessage  || message instanceof AssistantMessage ){

            if(message == Conversation.ROLLBACK_MESSAGE){
                // 避免特殊指令影响后续对话效果。将此前已添加的UserMessage移除。
                if(messages.size() > 0){
                    messages.removeLast();
                }
            }else{
                messages.add(message);
            }
        }else{
            logger.warn("不支持的消息类型：{}",message.getClass().getName());
        }
    }

    @Override
    public synchronized List<Message> messages() {
        // maxMessages一般设置为偶数，而实际调用此方法时一般是已添加了UserMessage。缩减缓存的历史消息size时，一般是移除一轮（User+Assistant）
        while (messages.size() > maxMessages+1) {
            messages.remove(0);
            messages.remove(0);
        }
        // 新消息列表对象，避免使用过程中污染原始列表对象
        List<Message> historyMessages = new ArrayList<>();
        var roleSystemMessage = roleSystemMessage();
        if(roleSystemMessage.isPresent()){
            historyMessages.add(roleSystemMessage.get());
        }
        historyMessages.addAll(messages);
        return historyMessages;
    }

}
