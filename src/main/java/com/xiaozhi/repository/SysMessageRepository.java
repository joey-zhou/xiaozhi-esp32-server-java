package com.xiaozhi.repository;

import com.xiaozhi.entity.SysMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 聊天记录数据访问层 - Spring Data JPA Repository
 *
 * @author Joey
 */
@Repository
public interface SysMessageRepository extends JpaRepository<SysMessage, Integer>, JpaSpecificationExecutor<SysMessage> {

    /**
     * 查询消息列表（支持多条件筛选）- 分页
     *
     * @param deviceId    设备 ID（可选）
     * @param sender      发送方（可选）
     * @param roleId      角色 ID（可选）
     * @param messageType 消息类型（可选）
     * @param pageable    分页参数
     * @return 消息分页列表
     */
    @Query(value = "SELECT m.*, d.deviceName, r.roleName " +
            "FROM sys_message m " +
            "LEFT JOIN sys_device d ON m.deviceId = d.deviceId " +
            "LEFT JOIN sys_role r ON m.roleId = r.roleId " +
            "WHERE 1=1 " +
            "AND (:deviceId IS NULL OR :deviceId = '' OR m.deviceId = :deviceId) " +
            "AND (:sender IS NULL OR :sender = '' OR m.sender = :sender) " +
            "AND (:roleId IS NULL OR m.roleId = :roleId) " +
            "AND (:messageType IS NULL OR :messageType = '' OR m.messageType = :messageType) " +
            "ORDER BY m.createTime DESC, m.sender DESC",
            countQuery = "SELECT COUNT(*) " +
            "FROM sys_message m " +
            "WHERE 1=1 " +
            "AND (:deviceId IS NULL OR :deviceId = '' OR m.deviceId = :deviceId) " +
            "AND (:sender IS NULL OR :sender = '' OR m.sender = :sender) " +
            "AND (:roleId IS NULL OR m.roleId = :roleId) " +
            "AND (:messageType IS NULL OR :messageType = '' OR m.messageType = :messageType)",
            nativeQuery = true)
    Page<SysMessage> findMessages(
            @Param("deviceId") String deviceId,
            @Param("sender") String sender,
            @Param("roleId") Integer roleId,
            @Param("messageType") String messageType,
            Pageable pageable);

    /**
     * 查找历史对话记录
     *
     * @param deviceId 设备 ID
     * @param roleId   角色 ID
     * @param limit    限制数量
     * @return 消息列表
     */
    @Query(value = "SELECT * FROM sys_message " +
            "WHERE state = '1' AND messageType = 'NORMAL' " +
            "AND deviceId = :deviceId AND roleId = :roleId " +
            "ORDER BY createTime DESC LIMIT :limit",
            nativeQuery = true)
    List<SysMessage> findHistoryMessages(
            @Param("deviceId") String deviceId,
            @Param("roleId") Integer roleId,
            @Param("limit") int limit);

    /**
     * 查找历史对话记录
     *
     * @param deviceId 设备 ID
     * @param roleId   角色 ID
     * @param limit    限制数量
     * @return 消息列表
     */
    @Query(value = "SELECT * FROM sys_message " +
            "WHERE state = '1' AND messageType = 'NORMAL' " +
            "AND deviceId = :deviceId AND roleId = :roleId " +
            "ORDER BY createTime DESC LIMIT :limit",
            nativeQuery = true)
    List<SysMessage> find(
            @Param("deviceId") String deviceId,
            @Param("roleId") Integer roleId,
            @Param("limit") int limit);

    /**
     * 查找指定时间戳后的历史对话记录
     *
     * @param deviceId     设备 ID
     * @param roleId       角色 ID
     * @param timeMillis   时间戳
     * @return 消息列表
     */
    @Query(value = "SELECT * FROM sys_message " +
            "WHERE state = '1' AND messageType = 'NORMAL' " +
            "AND deviceId = :deviceId AND roleId = :roleId " +
            "AND createTime >= :timeMillis",
            nativeQuery = true)
    List<SysMessage> findMessagesAfter(
            @Param("deviceId") String deviceId,
            @Param("roleId") Integer roleId,
            @Param("timeMillis") Instant timeMillis);

    /**
     * 查找指定时间戳后的历史对话记录
     *
     * @param deviceId     设备 ID
     * @param roleId       角色 ID
     * @param timeMillis   时间戳
     * @return 消息列表
     */
    @Query(value = "SELECT * FROM sys_message " +
            "WHERE state = '1' AND messageType = 'NORMAL' " +
            "AND deviceId = :deviceId AND roleId = :roleId " +
            "AND createTime >= :timeMillis",
            nativeQuery = true)
    List<SysMessage> findAfter(
            @Param("deviceId") String deviceId,
            @Param("roleId") Integer roleId,
            @Param("timeMillis") Instant timeMillis);

    /**
     * 根据消息 ID 查询消息
     *
     * @param messageId 消息 ID
     * @return 消息
     */
    @Query(value = "SELECT * FROM sys_message WHERE messageId = :messageId AND state = '1'", nativeQuery = true)
    Optional<SysMessage> findByIdAndActive(@Param("messageId") Integer messageId);

    /**
     * 批量保存消息
     *
     * @param messages 消息列表
     * @return 保存的消息列表
     */
    @Override
    @Transactional
    <S extends SysMessage> List<S> saveAll(Iterable<S> messages);

    /**
     * 删除消息（软删除）
     *
     * @param deviceId 设备 ID
     * @param userId   用户 ID
     * @return 影响行数
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE sys_message m " +
            "INNER JOIN sys_device d ON m.deviceId = d.deviceId " +
            "SET m.state = '0' " +
            "WHERE d.userId = :userId AND m.state = '1' " +
            "AND (:deviceId IS NULL OR :deviceId = '' OR m.deviceId = :deviceId)",
            nativeQuery = true)
    int deleteByDeviceAndUser(@Param("deviceId") String deviceId, @Param("userId") Integer userId);

    /**
     * 更新消息的音频数据信息
     *
     * @param deviceId   设备 ID
     * @param roleId     角色 ID
     * @param sender     发送方
     * @param createTime 创建时间
     * @param audioPath  音频路径
     * @return 影响行数
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE sys_message SET audioPath = :audioPath " +
            "WHERE deviceId = :deviceId AND roleId = :roleId " +
            "AND sender = :sender AND createTime = :createTime",
            nativeQuery = true)
    int updateAudioPath(@Param("deviceId") String deviceId,
                        @Param("roleId") Integer roleId,
                        @Param("sender") String sender,
                        @Param("createTime") String createTime,
                        @Param("audioPath") String audioPath);

    /**
     * 保存消息摘要
     *
     * @param deviceId           设备 ID
     * @param roleId             角色 ID
     * @param lastMessageTimestamp 最后消息时间戳
     * @param summary            摘要内容
     * @param promptTokens       prompt tokens
     * @param completionTokens   completion tokens
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO sys_summary (deviceId, roleId, lastMessageTimestamp, summary, promptTokens, completionTokens, createTime) " +
            "VALUES (:deviceId, :roleId, :lastMessageTimestamp, :summary, :promptTokens, :completionTokens, NOW())",
            nativeQuery = true)
    void saveSummary(@Param("deviceId") String deviceId,
                     @Param("roleId") Integer roleId,
                     @Param("lastMessageTimestamp") Instant lastMessageTimestamp,
                     @Param("summary") String summary,
                     @Param("promptTokens") Integer promptTokens,
                     @Param("completionTokens") Integer completionTokens);

    /**
     * 查找最新的消息摘要
     *
     * @param deviceId 设备 ID
     * @param roleId   角色 ID
     * @return 摘要信息
     */
    @Query(value = "SELECT deviceId, roleId, lastMessageTimestamp, summary, createTime " +
            "FROM sys_summary " +
            "WHERE deviceId = :deviceId AND roleId = :roleId " +
            "ORDER BY createTime DESC LIMIT 1",
            nativeQuery = true)
    List<Object[]> findLastSummary(@Param("deviceId") String deviceId, @Param("roleId") Integer roleId);

    /**
     * 删除摘要
     *
     * @param roleId     角色 ID
     * @param deviceId   设备 ID
     * @param createTime 创建时间
     * @return 影响行数
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM sys_summary WHERE roleId = :roleId AND deviceId = :deviceId AND createTime = :createTime",
            nativeQuery = true)
    int deleteSummary(@Param("roleId") Integer roleId,
                      @Param("deviceId") String deviceId,
                      @Param("createTime") Instant createTime);

    // ==================== MessageMapper 迁移方法 ====================

    /**
     * 添加消息（MessageMapper.add）
     */
    default int add(SysMessage message) {
        save(message);
        return 1;
    }

    /**
     * 批量保存消息（MessageMapper.saveAll）
     */
    default int saveAll(List<SysMessage> messages) {
        return saveAll((Iterable<SysMessage>) messages).size();
    }

    /**
     * 删除消息（MessageMapper.delete）
     */
    default int deleteMessage(SysMessage message) {
        if (message.getMessageId() != null) {
            deleteById(message.getMessageId());
            return 1;
        }
        return 0;
    }

    /**
     * 查询消息列表（MessageMapper.query）
     */
    default List<SysMessage> query(SysMessage message) {
        return findAll();
    }

    /**
     * 查找历史对话记录（MessageMapper.find）
     */
    default List<SysMessage> find(String deviceId, int roleId, int limit) {
        return findHistoryMessages(deviceId, roleId, limit);
    }

    /**
     * 查找指定时间戳后的历史对话记录（MessageMapper.findAfter）
     */
    default List<SysMessage> findAfter(String deviceId, int roleId, Instant timeMillis) {
        return findMessagesAfter(deviceId, roleId, timeMillis);
    }

    /**
     * 更新消息的音频数据信息（MessageMapper.updateMessageByAudioFile）
     */
    default void updateMessageByAudioFile(SysMessage sysMessage) {
        updateAudioPath(
                sysMessage.getDeviceId(),
                sysMessage.getRoleId(),
                sysMessage.getSender(),
                sysMessage.getCreateTime().toString(),
                sysMessage.getAudioPath()
        );
    }

    /**
     * 根据消息 ID 查询消息（MessageMapper.findById）
     */
    default SysMessage findByMessageId(Integer messageId) {
        return findByIdAndActive(messageId).orElse(null);
    }
}
