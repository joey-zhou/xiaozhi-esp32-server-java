package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.entity.SysSummary;
import org.springframework.ai.chat.messages.Message;

import java.time.Instant;
import java.util.List;

/**
 * 聊天记忆接口，全局对象，不针对单个会话，而是负责全局记忆的存储策略及针对不同类型数据库的适配。
 * 方向一：不同于SysMessageService，此接口应该是一个更高的抽象层，更多是负责存储策略而并非底层存储的增删改查。
 * 方向二：理解为与SysMessageService同层级的同类功能的接口，但必须支持批量保存与数据库类型适配。
 * 当前设计选择方向二：支持批量操作，以求减少IO，提升服务器支持更大的吞吐。
 * 已经参考了spring ai 的ChatMemory接口，暂时放弃spring ai 的ChatMemory。
 * 以后使用ChatClient与Advisor时直接实现一个更本地友好的ChatMemoryAdvisor。
 * Conversation则是参考了 langchain4j 的ChatMemory。
 *
 */
public interface ChatMemory {
    String MESSAGE_TYPE_KEY = "SYS_MESSAGE_TYPE";
    String TIME_MILLIS_KEY = "TIME_MILLIS";
    String AUDIO_PATH = "AUDIO_PATH";



    /**
     * 保存会话的基本信息（ID，摘要，totalTokens,创建时间）
     * @param summary
     */
    void save(SysSummary summary);

    /**
     * 查询最近的Conversation Summary
     * @param deviceId
     * @param roleId
     * @return
     */
    SysSummary findLastSummary(String deviceId, int roleId);

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


}