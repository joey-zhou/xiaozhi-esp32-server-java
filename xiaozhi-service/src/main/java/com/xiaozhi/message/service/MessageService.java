package com.xiaozhi.message.service;

import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.resp.MessageResp;
import com.xiaozhi.common.model.resp.PageResp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

public interface MessageService {

    PageResp<MessageResp> page(int pageNo, int pageSize, String deviceId, String deviceName,
                               String sender, String messageType, Integer roleId,
                               Date startTime, Date endTime, Integer userId);

    void delete(Integer messageId);

    int deleteByDeviceId(String deviceId);

    MessageBO getBO(Integer messageId);

    int saveAll(List<MessageBO> messages);

    List<MessageBO> listHistory(String deviceId, Integer roleId, int limit);

    List<MessageBO> listHistoryAfter(String deviceId, Integer roleId, Instant time);

    void updateAudioInfo(String deviceId,
                         Integer roleId,
                         String sender,
                         LocalDateTime createTime,
                         String audioPath,
                         BigDecimal sttDuration,
                         BigDecimal ttsDuration);
}
