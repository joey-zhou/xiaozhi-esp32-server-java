package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.SummaryBO;
import org.springframework.ai.chat.messages.Message;

import java.time.Instant;
import java.util.List;

/**
 * 聊天记忆接口，全局对象，不针对单个会话，而是负责全局记忆的存储策略及针对不同类型数据库的适配。
 * 方向一：不同于 MessageService，此接口应该是一个更高的抽象层，更多是负责存储策略而并非底层存储的增删改查。
 * 方向二：理解为与 MessageService 同层级的同类功能的接口，但必须支持批量保存与数据库类型适配。
 * 当前设计选择方向二：支持批量操作，以求减少IO，提升服务器支持更大的吞吐。
 * 已经参考了spring ai 的ChatMemory接口，暂时放弃spring ai 的ChatMemory。
 * 以后使用ChatClient与Advisor时直接实现一个更本地友好的ChatMemoryAdvisor。
 * Conversation则是参考了 langchain4j 的ChatMemory。
 *
 */
public interface ChatMemory {
    String TIME_MILLIS_KEY = "TIME_MILLIS";
    String AUDIO_PATH = "AUDIO_PATH";
    String USAGE_KEY = "llm_usage";  // 用于存储LLM使用情况的键



    /**
     * 保存会话的基本信息（ID，摘要，totalTokens,创建时间）
     * @param summary
     */
    void save(SummaryBO summary);

    /**
     * 查询最近的Conversation Summary
     * @param ownerId 聊天参与者标识（设备场景: deviceId, Web 场景: userId）
     * @param roleId
     * @return
     */
    SummaryBO findLastSummary(String ownerId, int roleId);

    /**
     * 按 ownerId + roleId 获取历史对话消息列表（设备场景：跨 session 聚合）。
     *
     * @param ownerId 聊天参与者标识（设备场景: deviceId）
     * @param roleId 角色ID
     * @param limit 限制数量，此参数对于性能是必要的。
     * @return 消息列表，按 createTime 升序
     */
    List<Message> find(String ownerId, int roleId, int limit);

    /**
     * 按 sessionId 获取历史对话消息列表（Web 场景：按会话隔离）。
     * 与 {@link #find(String, int, int)} 参数数量不同构成方法重载。
     *
     * @param sessionId 会话 ID
     * @param limit 限制数量
     * @return 消息列表，按 createTime 升序
     */
    List<Message> find(String sessionId, int limit);

    /**
     * 获取历史对话消息列表
     * @param ownerId 聊天参与者标识（设备场景: deviceId, Web 场景: userId）
     * @param roleId 角色ID
     * @param timeMillis 在这个时间戳后的消息
     * @return
     */
    List<Message> find(String ownerId, int roleId, Instant timeMillis);
    /**
     * 清除历史记录
     * 不是提供给Conversation使用，而是用于强制使其失忆的场景。
     *
     * @param ownerId 聊天参与者标识（设备场景: deviceId, Web 场景: userId）
     */
    void delete(String ownerId, int roleId);


}
