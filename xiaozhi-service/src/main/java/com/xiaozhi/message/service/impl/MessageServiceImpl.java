package com.xiaozhi.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.resp.MessageResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.event.ConversationHistoryClearedEvent;
import com.xiaozhi.message.convert.MessageConvert;
import com.xiaozhi.message.dal.mysql.dataobject.MessageDO;
import com.xiaozhi.message.dal.mysql.mapper.MessageMapper;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.utils.AudioUtils;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class MessageServiceImpl implements MessageService {

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private MessageConvert messageConvert;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public PageResp<MessageResp> page(int pageNo, int pageSize, String deviceId, String deviceName,
                                      String sender, String messageType, Integer roleId,
                                      Date startTime, Date endTime, Integer userId) {
        Page<MessageResp> page = new Page<>(pageNo, pageSize);
        IPage<MessageResp> result = messageMapper.selectPageResp(page, deviceId, deviceName, sender, messageType, roleId, startTime, endTime, userId);
        return new PageResp<>(
            result.getRecords(),
            result.getTotal(),
            Math.toIntExact(result.getCurrent()),
            Math.toIntExact(result.getSize())
        );
    }

    @Override
    @Transactional
    public void delete(Integer messageId) {
        if (messageId == null) {
            throw new IllegalArgumentException("消息ID不能为空");
        }
        MessageBO existing = getBO(messageId);
        if (existing == null) {
            throw new ResourceNotFoundException("消息不存在或已删除");
        }

        if (StringUtils.hasText(existing.getAudioPath())) {
            AudioUtils.deleteFile(existing.getAudioPath());
        }
        LambdaUpdateWrapper<MessageDO> updateWrapper = new LambdaUpdateWrapper<MessageDO>()
            .eq(MessageDO::getMessageId, messageId)
            .eq(MessageDO::getState, MessageBO.STATE_ENABLED)
            .set(MessageDO::getState, MessageBO.STATE_DELETED);
        if (messageMapper.update(null, updateWrapper) <= 0) {
            throw new IllegalStateException("删除消息失败");
        }
    }

    @Override
    @Transactional
    public int deleteByDeviceId(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return 0;
        }

        String audioDeviceId = deviceId.replace(":", "-");
        LocalDate today = LocalDate.now();
        for (int i = 0; i <= AudioUtils.AUDIO_RETENTION_DAYS; i++) {
            String date = today.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            Path deviceDir = Path.of(AudioUtils.AUDIO_PATH, date, audioDeviceId);
            AudioUtils.deleteDirectory(deviceDir);
        }
        eventPublisher.publishEvent(new ConversationHistoryClearedEvent(this, deviceId));
        LambdaUpdateWrapper<MessageDO> updateWrapper = new LambdaUpdateWrapper<MessageDO>()
            .eq(MessageDO::getDeviceId, deviceId)
            .eq(MessageDO::getState, MessageBO.STATE_ENABLED)
            .set(MessageDO::getState, MessageBO.STATE_DELETED);
        return messageMapper.update(null, updateWrapper);
    }

    @Override
    public MessageBO getBO(Integer messageId) {
        if (messageId == null) {
            return null;
        }

        LambdaQueryWrapper<MessageDO> queryWrapper = new LambdaQueryWrapper<MessageDO>()
            .eq(MessageDO::getMessageId, messageId)
            .eq(MessageDO::getState, MessageBO.STATE_ENABLED);
        MessageDO messageDO = messageMapper.selectOne(queryWrapper);
        return messageDO == null ? null : messageConvert.toBO(messageDO);
    }

    @Override
    @Transactional
    public int saveAll(List<MessageBO> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        int rows = 0;
        for (MessageBO message : messages) {
            MessageDO messageDO = messageConvert.toDO(message);
            if (!StringUtils.hasText(messageDO.getState())) {
                messageDO.setState(MessageBO.STATE_ENABLED);
            }
            if (!StringUtils.hasText(messageDO.getMessageType())) {
                messageDO.setMessageType(MessageBO.MESSAGE_TYPE_NORMAL);
            }
            if (messageDO.getCreateTime() == null) {
                messageDO.setCreateTime(now);
            }
            if (messageDO.getUpdateTime() == null) {
                messageDO.setUpdateTime(messageDO.getCreateTime());
            }
            if (messageDO.getStatDate() == null) {
                messageDO.setStatDate(today);
            }
            if (messageMapper.insert(messageDO) > 0) {
                rows++;
            }
        }
        return rows;
    }

    @Override
    public List<MessageBO> listHistory(String deviceId, Integer roleId, int limit) {
        if (!StringUtils.hasText(deviceId) || roleId == null || limit <= 0) {
            return Collections.emptyList();
        }
        return messageMapper.selectList(new LambdaQueryWrapper<MessageDO>()
                .eq(MessageDO::getState, MessageBO.STATE_ENABLED)
                .eq(MessageDO::getDeviceId, deviceId)
                .eq(MessageDO::getRoleId, roleId)
                .orderByDesc(MessageDO::getCreateTime)
                .last("LIMIT " + limit))
            .stream()
            .map(messageConvert::toBO)
            .toList();
    }

    @Override
    public List<MessageBO> listHistoryAfter(String deviceId, Integer roleId, Instant time) {
        if (!StringUtils.hasText(deviceId) || roleId == null || time == null) {
            return Collections.emptyList();
        }
        LocalDateTime createTime = LocalDateTime.ofInstant(time, ZoneId.systemDefault());
        return messageMapper.selectList(new LambdaQueryWrapper<MessageDO>()
                .eq(MessageDO::getState, MessageBO.STATE_ENABLED)
                .eq(MessageDO::getDeviceId, deviceId)
                .eq(MessageDO::getRoleId, roleId)
                .ge(MessageDO::getCreateTime, createTime)
                .orderByAsc(MessageDO::getCreateTime)
                .orderByDesc(MessageDO::getSender))
            .stream()
            .map(messageConvert::toBO)
            .toList();
    }

    @Override
    @Transactional
    public void updateAssistantAudio(String deviceId, Integer roleId,
                                     LocalDateTime createTime, String audioPath,
                                     BigDecimal ttsDuration) {
        if (!StringUtils.hasText(deviceId) || roleId == null || createTime == null) {
            return;
        }

        // 1. 找到 assistant 消息的 messageId
        LambdaQueryWrapper<MessageDO> query = new LambdaQueryWrapper<MessageDO>()
            .eq(MessageDO::getDeviceId, deviceId)
            .eq(MessageDO::getRoleId, roleId)
            .eq(MessageDO::getSender, MessageBO.SENDER_ASSISTANT)
            .eq(MessageDO::getCreateTime, createTime)
            .select(MessageDO::getMessageId);
        MessageDO messageDO = messageMapper.selectOne(query);
        if (messageDO == null) {
            return;
        }

        // 2. 更新 audioPath
        if (StringUtils.hasText(audioPath)) {
            LambdaUpdateWrapper<MessageDO> msgUpdate = new LambdaUpdateWrapper<MessageDO>()
                .eq(MessageDO::getMessageId, messageDO.getMessageId())
                .set(MessageDO::getAudioPath, audioPath)
                .set(MessageDO::getUpdateTime, LocalDateTime.now());
            messageMapper.update(null, msgUpdate);
        }
    }

}
