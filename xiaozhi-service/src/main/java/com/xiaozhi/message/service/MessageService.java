package com.xiaozhi.message.service;

import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.resp.ConversationResp;
import com.xiaozhi.common.model.resp.MessageResp;
import com.xiaozhi.common.model.resp.PageResp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

public interface MessageService {

    PageResp<MessageResp> page(int pageNo, int pageSize, String deviceId, String deviceName,
                               String sender, String messageType, Integer roleId,
                               Date startTime, Date endTime, Integer userId, String sessionId,
                               String source);

    PageResp<ConversationResp> conversationPage(int pageNo, int pageSize, Integer userId, Integer roleId, String source);

    void delete(Integer messageId);

    int deleteByDeviceId(String deviceId);

    MessageBO getBO(Integer messageId);

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

    List<MessageBO> listHistoryAfter(String deviceId, Integer roleId, Instant time);

    /**
     * 更新 assistant 消息的音频路径，并更新关联的 metrics 记录中的 ttsDuration。
     */
    void updateAssistantAudio(String deviceId, Integer roleId,
                              LocalDateTime createTime, String audioPath,
                              java.math.BigDecimal ttsDuration);
}
