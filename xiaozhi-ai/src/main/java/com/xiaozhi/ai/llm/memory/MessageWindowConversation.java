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

        logger.info("加载设备{}的对话历史", device.getDeviceId());
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


    @Override
    public synchronized void add(Message message) {
        if(message instanceof UserMessage || message instanceof AssistantMessage || message instanceof ToolResponseMessage){
            messages.add(message);
        }else{
            logger.warn("不支持的消息类型：{}",message.getClass().getName());
        }
    }

    @Override
    public synchronized List<Message> messages() {
        // 按对话组裁剪：简单组=[User,Assistant](2条)，工具组=[User,Assistant(toolCall),Tool,Assistant(final)](4条)
        while (messages.size() > maxMessages + 1) {
            if (messages.size() >= 2 && messages.get(1) instanceof AssistantMessage am
                    && am.getToolCalls() != null && !am.getToolCalls().isEmpty()
                    && messages.size() >= 4) {
                // 工具对话组：移除 4 条 [User, Assistant(toolCall), Tool, Assistant(final)]
                for (int i = 0; i < 4 && !messages.isEmpty(); i++) {
                    messages.remove(0);
                }
            } else {
                // 简单对话组：移除 2 条 [User, Assistant]
                messages.remove(0);
                if (!messages.isEmpty()) {
                    messages.remove(0);
                }
            }
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
