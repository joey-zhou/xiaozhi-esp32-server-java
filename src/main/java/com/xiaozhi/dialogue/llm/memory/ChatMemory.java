package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.entity.SysMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;

import java.time.Instant;
import java.util.List;

/**
 * 聊天记忆接口，全局对象，不针对单个会话，而是负责全局记忆的存储策略及针对不同类型数据库的适配。。
 * 方向一：不同于SysMessageService，此接口应该是一个更高的抽象层，更多是负责存储策略而并非底层存储的增删改查。
 * 方向二：理解为与SysMessageService同层级的同类功能的接口，但必须支持批量保存与数据库类型适配。
 * 当前设计选择方向二：支持批量操作，以求减少IO，提升服务器支持更大的吞吐。
 * 已经参考了spring ai 的ChatMemory接口，暂时放弃spring ai 的ChatMemory。
 * 以后使用ChatClient与Advisor时直接实现一个更本地友好的ChatMemoryAdvisor。
 * Conversation则是参考了 langchain4j 的ChatMemory。
 *
 */
public interface ChatMemory {
    public static final String MESSAGE_TYPE_KEY = "SYS_MESSAGE_TYPE";
    public static final String TIME_MILLIS_KEY = "TIME_MILLIS";
    public static final String USAGE_KEY = "llm_usage";  // 用于存储LLM使用情况的键
    /**
     * 添加消息。
     * 支持批量，对于注重性能的实现是很有必要的。
     */
    void save(String deviceId, Integer roleId, String sessionId, List<Message> messages);

    /**
     * 获取历史对话消息列表
     *
     * @param deviceId 设备ID
     * @param roleId 角色ID
     * @param limit 限制数量，此参数对于性能是必要的。
     * @return 消息列表
     */
    List<Message> find(String deviceId, int roleId, int limit);

    /**
     * 获取历史对话消息列表
     * @param deviceId 指定设备ID
     * @param roleId 角色ID
     * @param timeMillis 在这个时间戳后的消息
     * @return
     */
    List<Message> find(String deviceId, int roleId, Instant timeMillis);
    /**
     * 清除设备的历史记录
     * 不是提供给Conversation使用，而是用于FUNCTION_CALL 场景使用。或者其它强制使其失忆的场景。
     *
     * @param deviceId 设备ID
     */
    void delete(String deviceId, int roleId);

    static void setSysMessageType(Message message, String messageType){
        message.getMetadata().put(MESSAGE_TYPE_KEY, messageType);
    }

    static String getSysMessageType(Message message){
        return (String) message.getMetadata().getOrDefault(MESSAGE_TYPE_KEY,SysMessage.MESSAGE_TYPE_NORMAL);
    }

    static void setTimeMillis(Message message, Long timeMillis){
        message.getMetadata().put(TIME_MILLIS_KEY, timeMillis);
    }

    static Long getTimeMillis(Message message){
        return (Long) message.getMetadata().getOrDefault(TIME_MILLIS_KEY,System.currentTimeMillis());
    }

    static Integer getFirstModelResponseTime(Message message){
        return (Integer)message.getMetadata().get(ChatSession.ATTR_FIRST_MODEL_RESPONSE_TIME);
    }

    static Integer getFirstTtsResponseTime(Message message){
        return (Integer)message.getMetadata().get(ChatSession.ATTR_FIRST_TTS_RESPONSE_TIME);
    }
    static Usage getUsage(AssistantMessage message){
        return (Usage)message.getMetadata().get(USAGE_KEY);
    }
    static void setUsage(AssistantMessage message, Usage usage){
        message.getMetadata().put(USAGE_KEY, usage);
    }
    static void setTokens(Message message, Integer tokens){
        message.getMetadata().put("tokens", tokens);
    }
    static Integer getTokens(Message message){
        return (Integer)message.getMetadata().get("tokens");
    }
}