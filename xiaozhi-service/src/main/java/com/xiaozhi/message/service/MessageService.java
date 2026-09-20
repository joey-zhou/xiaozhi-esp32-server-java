package com.xiaozhi.message.service;

import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.message.model.MessageProjection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MessageService {

    PageResult<MessageProjection> page(int pageNo, int pageSize, String deviceId, String deviceName,
                                       String sender, String messageType, Integer roleId,
                                       LocalDateTime startTime, LocalDateTime endTime, Integer userId, String sessionId,
                                       String source);


    void delete(Long messageId);

    int deleteByDeviceId(String deviceId);

    /** 逻辑删除这些会话的全部消息。Web 会话没有录音，不清理文件。 */
    int deleteBySessionIds(Collection<String> sessionIds);

    MessageBO getBO(Long messageId);

    int saveAll(List<MessageBO> messages);

    /**
     * 按 ownerId（deviceId）+ roleId 查询最近 limit 条历史消息，按时间升序返回（即会话上下文顺序）。
     * 适用于设备场景（跨 session 聚合）。
     */
    List<MessageBO> listHistory(String deviceId, Integer roleId, int limit);

    /**
     * 按 sessionId 查询最近 limit 条历史消息，按时间升序返回（即会话上下文顺序）。
     * 适用于 Web 场景（按会话隔离）。
     */
    List<MessageBO> listHistory(String sessionId, int limit);

    /**
     * 查询 createTime 严格晚于 time 的历史消息，按 createTime、messageId 升序返回。
     * <p>
     * 排序必须与写入顺序一致（messageId 自增即写入顺序），工具调用与工具响应之间乱序
     * 会被 OpenAI 兼容协议直接拒绝。
     */
    List<MessageBO> listHistoryAfter(String deviceId, Integer roleId, Instant time);

    /**
     * 按 sessionId 查询 createTime 严格晚于 time 的历史消息，排序同 {@link #listHistoryAfter(String, Integer, Instant)}。
     * 适用于 Web 场景（按会话隔离）；不做重载，漏写 roleId 时不会悄悄变成按会话查。
     */
    List<MessageBO> listSessionHistoryAfter(String sessionId, Instant time);

    /**
     * 更新 assistant 消息的音频路径，并更新关联的 metrics 记录中的 ttsDuration。
     */
    void updateAssistantAudio(String deviceId, Integer roleId,
                              LocalDateTime createTime, String audioPath,
                              BigDecimal ttsDuration);

    /**
     * 把播放途中被打断的 assistant 消息截到用户听到的文本；spokenText 为空则连同 metrics 一起删除。
     */
    /**
     * 清理超过保留期的对话录音：从存储层删文件，并把 audioPath 置空。
     * <p>
     * 只清音频，消息文本保留。置空是必须的 —— 本地存储模式下文件已被目录级清理删掉，
     * 列里还留着路径会让历史消息渲染出点不开的播放器。
     *
     * @return 清理掉的消息条数
     */
    int purgeExpiredAudio(int retentionDays, int batchSize);

    /**
     * 统计 audioPath 以 prefix 开头的对话录音条数。
     * <p>
     * 换对象存储前用它数存量：库里只存地址不存归属，前缀是唯一能认出「这条录音属于哪个存储」的线索。
     */
    long countStoredPathsWithPrefix(String prefix);

    void truncateAssistant(String deviceId, Integer roleId, LocalDateTime createTime, String spokenText);
}
