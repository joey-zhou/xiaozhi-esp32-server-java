package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.entity.SysRole;
import org.springframework.ai.chat.messages.*;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 限定消息条数（消息窗口）的Conversation实现。根据不同的策略，可实现聊天会话的持久化、加载、清除等功能。
 */
public class MessageWindowConversation extends Conversation {
    // 历史记录默认限制数量
    public static final int DEFAULT_HISTORY_LIMIT = 0;
    private final ChatMemory chatMemory;
    private final int maxMessages;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MessageWindowConversation.class);


    public MessageWindowConversation(SysDevice device, SysRole role, String sessionId, int maxMessages, ChatMemory chatMemory){
        super(device, role, sessionId);
        this.maxMessages = maxMessages;
        this.chatMemory = chatMemory;
        logger.info("加载设备{}的普通消息(SysMessage.MESSAGE_TYPE_NORMAL)作为对话历史",device.getDeviceId());
        List<Message> history = chatMemory.find(device.getDeviceId(), role.getRoleId(), maxMessages);
        super.messages.addAll(history) ;
    }

    public static class Builder {
        private SysDevice device;
        private SysRole role;
        private String sessionId;
        private int maxMessages;
        private ChatMemory chatMemory;

        public Builder device(SysDevice device) {
            this.device = device;
            return this;
        }

        public Builder role(SysRole role) {
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
     * @param timeMillis
     */
    @Override
    public void add(Message message, Long timeMillis) {

        if(message instanceof UserMessage userMsg){
            ChatMemory.setSysMessageType(userMsg, SysMessage.MESSAGE_TYPE_NORMAL);
            ChatMemory.setTimeMillis(userMsg, timeMillis);
            messages.add(userMsg);
            return;
        }
        if(message instanceof AssistantMessage assistantMessage){
            ChatMemory.setSysMessageType(assistantMessage, SysMessage.MESSAGE_TYPE_NORMAL);
            ChatMemory.setTimeMillis(assistantMessage, timeMillis);

            //boolean hasToolCalls = assistantMessage.hasToolCalls();
            // 检查元数据中是否包含工具调用标识
            String toolName = (String) assistantMessage.getMetadata().get("toolName");

            // 发生了工具调用，获取函数调用的名称，通过名称反查类型
            // String functionName = chatResponse.getMetadata().get("function_name");

            boolean hasToolCalls = StringUtils.hasText(toolName);

            // 非function消息才加入对话历史，避免调用混乱。
            // 这个逻辑面对更多的工具调用时，可能是值得商榷的。有些工具调用的结果直接作为AssistantMessage加入对话历史并不会影响对话效果。
            // 后续考虑：在XiaozhiToolCallingManager实现类里，包装出的AssistantMessage由工具来添加标识是否影响对话效果。

            if(!hasToolCalls){
                // 更新缓存
                messages.add(assistantMessage);

                Usage usage = ChatMemory.getUsage(assistantMessage);
            // 判断消息类型（不是spring-ai的消息类型），同一轮对话里UserMessage和AssistantMessage的messageType相同
            String messageType = hasToolCalls ? SysMessage.MESSAGE_TYPE_FUNCTION_CALL : SysMessage.MESSAGE_TYPE_NORMAL;
            String deviceId = device().getDeviceId();
            int roleId = role().getRoleId();

            List<Message> toSave = new ArrayList<>();

            // 如果本轮对话是function_call或mcp调用(最后一条信息的类型)，把用户的消息类型也修正为同样类型
            for (int i = this.messages.size() - 1; i >= 0; i--) {
                Message msg = this.messages.get(i);
                if (msg instanceof UserMessage userMessage) {
                    ChatMemory.setSysMessageType(userMessage, messageType);
                    ChatMemory.setTokens(userMessage, usage.getPromptTokens());
                    toSave.add(userMessage);
                }
            }

            String response = assistantMessage.getText();
            if (StringUtils.hasText(response)) {
                ChatMemory.setSysMessageType(assistantMessage, messageType);
                ChatMemory.setTimeMillis(assistantMessage, timeMillis);
                ChatMemory.setTokens(assistantMessage, usage.getCompletionTokens());
                toSave.add(assistantMessage);
            }
            chatMemory.save(deviceId, roleId, sessionId(), toSave);

            }else{
                //对于有工具调用标记的Assistant消息，则将此前已添加的UserMessage移除。
                messages.removeLast();
            }
        }
    }

    @Override
    public List<Message> messages() {
        String roleDesc = role().getRoleDesc();
        // 添加设备地址信息到系统提示词中
        String deviceLocation = device().getLocation();
        if (StringUtils.hasText(deviceLocation)) {
            roleDesc = (StringUtils.hasText(roleDesc) ? roleDesc + "\n\n" : "") +
                       "当前位置：" + deviceLocation + "。如用户没有说明具体位置，则根据当前位置提供相关服务，如天气查询等。";
        }
        SystemMessage systemMessage = new SystemMessage(StringUtils.hasText(roleDesc)?roleDesc:"");
        // maxMessages一般设置为偶数，而实际调用此方法时一般是已添加了UserMessage。缩减缓存的历史消息size时，一般是移除一轮（User+Assistant）
        while (messages.size() > maxMessages+1) {
            messages.remove(0);
        }
        // 新消息列表对象，避免使用过程中污染原始列表对象
        List<Message> historyMessages = new ArrayList<>();
        historyMessages.add(systemMessage);
        historyMessages.addAll(messages);
        return historyMessages;
    }

}
