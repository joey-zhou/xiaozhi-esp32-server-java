package com.xiaozhi.message.service.impl;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.event.ConversationHistoryClearedEvent;
import com.xiaozhi.message.convert.MessageConvert;
import com.xiaozhi.message.dal.mysql.dataobject.MessageDO;
import com.xiaozhi.message.dal.mysql.mapper.MessageMapper;
import com.xiaozhi.message.model.MessageProjection;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.AudioUtils;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl implements MessageService {

    @Resource
    private RuntimePathConfig runtimePathConfig;

    @Resource
    private StorageServiceFactory storageServiceFactory;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private MessageConvert messageConvert;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public PageResult<MessageProjection> page(int pageNo, int pageSize, String deviceId, String deviceName,
                                              String sender, String messageType, Integer roleId,
                                              Date startTime, Date endTime, Integer userId, String sessionId,
                                              String source) {
        Page<MessageProjection> page = new Page<>(pageNo, pageSize);
        IPage<MessageProjection> iPage = messageMapper.selectPage(page, deviceId, deviceName, sender, messageType, roleId, startTime, endTime, userId, sessionId, source);
        return new PageResult<>(iPage.getRecords(), iPage.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional
    public int deleteBySessionIds(Collection<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0;
        }
        return messageMapper.update(null, new LambdaUpdateWrapper<MessageDO>()
            .in(MessageDO::getSessionId, sessionIds)
            .eq(MessageDO::getState, MessageBO.STATE_ENABLED)
            .set(MessageDO::getState, MessageBO.STATE_DELETED));
    }

    @Override
    @Transactional
    public void delete(Long messageId) {
        if (messageId == null) {
            throw new IllegalArgumentException("消息ID不能为空");
        }
        MessageBO existing = getBO(messageId);
        if (existing == null) {
            throw new ResourceNotFoundException("消息不存在或已删除");
        }

        LambdaUpdateWrapper<MessageDO> updateWrapper = new LambdaUpdateWrapper<MessageDO>()
            .eq(MessageDO::getMessageId, messageId)
            .eq(MessageDO::getState, MessageBO.STATE_ENABLED)
            .set(MessageDO::getState, MessageBO.STATE_DELETED);
        if (messageMapper.update(null, updateWrapper) <= 0) {
            throw new IllegalStateException("删除消息失败");
        }
        if (StringUtils.hasText(existing.getAudioPath())) {
            runAfterCommit(() -> AudioUtils.deleteFile(existing.getAudioPath()));
        }
    }

    @Override
    @Transactional
    public int deleteByDeviceId(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return 0;
        }

        // 先改数据库，成功后再做本地文件删除、发事件这些不可逆/不可回滚的副作用，
        // 避免数据库更新失败时文件已删、下游已收到清空事件，但消息记录仍在
        LambdaUpdateWrapper<MessageDO> updateWrapper = new LambdaUpdateWrapper<MessageDO>()
            .eq(MessageDO::getDeviceId, deviceId)
            .eq(MessageDO::getState, MessageBO.STATE_ENABLED)
            .set(MessageDO::getState, MessageBO.STATE_DELETED);
        int updated = messageMapper.update(null, updateWrapper);

        runAfterCommit(() -> deleteAudioDirectories(deviceId));
        eventPublisher.publishEvent(new ConversationHistoryClearedEvent(this, deviceId));
        return updated;
    }

    /**
     * 删除该设备在保留期内每一天的音频目录。
     */
    private void deleteAudioDirectories(String deviceId) {
        String audioDeviceId = deviceId.replace(":", "-");
        LocalDate today = LocalDate.now();
        for (int i = 0; i <= AudioUtils.AUDIO_RETENTION_DAYS; i++) {
            String date = today.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            Path deviceDir = runtimePathConfig.resolveStorageKey(
                    Path.of(AudioUtils.AUDIO_PATH, date, audioDeviceId).toString());
            AudioUtils.deleteDirectory(deviceDir);
        }
    }

    /**
     * 事务提交后执行不可回滚的副作用；没有事务上下文时直接执行。
     */
    private void runAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    @Override
    public MessageBO getBO(Long messageId) {
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
            // message/toolCalls 是 text 列，超长会导致这条 insert 报错、整个事务连同同一轮的其它消息一起回滚
            messageDO.setMessage(truncateToTextColumn(messageDO.getMessage()));
            messageDO.setToolCalls(truncateToTextColumn(messageDO.getToolCalls()));
            if (messageMapper.insert(messageDO) > 0) {
                rows++;
            }
        }
        return rows;
    }

    // 略低于 MySQL text 列 65535 字节上限，留出安全余量
    private static final int TEXT_COLUMN_MAX_BYTES = 65000;
    private static final String TRUNCATE_SUFFIX = "...(内容过长已截断)";

    /**
     * 按 UTF-8 字节数截断到 text 列容量内，避免超长内容让整条 insert 报错、拖累整个事务回滚。
     */
    private static String truncateToTextColumn(String value) {
        if (value == null) {
            return null;
        }
        if (value.getBytes(StandardCharsets.UTF_8).length <= TEXT_COLUMN_MAX_BYTES) {
            return value;
        }
        int budget = TEXT_COLUMN_MAX_BYTES - TRUNCATE_SUFFIX.getBytes(StandardCharsets.UTF_8).length;
        int low = 0;
        int high = value.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (value.substring(0, mid).getBytes(StandardCharsets.UTF_8).length <= budget) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return value.substring(0, low) + TRUNCATE_SUFFIX;
    }

    @Override
    public List<MessageBO> listHistory(String deviceId, Integer roleId, int limit) {
        if (!StringUtils.hasText(deviceId) || roleId == null || limit <= 0) {
            return Collections.emptyList();
        }
        List<MessageBO> desc = messageMapper.selectList(new LambdaQueryWrapper<MessageDO>()
                .eq(MessageDO::getState, MessageBO.STATE_ENABLED)
                .eq(MessageDO::getDeviceId, deviceId)
                .eq(MessageDO::getRoleId, roleId)
                .orderByDesc(MessageDO::getCreateTime)
                .orderByDesc(MessageDO::getMessageId)
                .last("LIMIT " + limit))
            .stream()
            .map(messageConvert::toBO)
            .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(desc);
        return desc;
    }

    @Override
    public List<MessageBO> listHistory(String sessionId, int limit) {
        if (!StringUtils.hasText(sessionId) || limit <= 0) {
            return Collections.emptyList();
        }
        List<MessageBO> desc = messageMapper.selectList(new LambdaQueryWrapper<MessageDO>()
                .eq(MessageDO::getState, MessageBO.STATE_ENABLED)
                .eq(MessageDO::getSessionId, sessionId)
                .orderByDesc(MessageDO::getCreateTime)
                .orderByDesc(MessageDO::getMessageId)
                .last("LIMIT " + limit))
            .stream()
            .map(messageConvert::toBO)
            .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(desc);
        return desc;
    }

    // 摘要一旦长期卡住会攒下无限量未摘要消息，这里兜底只取最近这么多条，避免一次性
    // 全部读进内存、整体格式化进主 LLM 提示词
    private static final int MAX_HISTORY_AFTER_ROWS = 500;

    @Override
    public List<MessageBO> listHistoryAfter(String deviceId, Integer roleId, Instant time) {
        if (!StringUtils.hasText(deviceId) || roleId == null || time == null) {
            return Collections.emptyList();
        }
        return latestAfter(new LambdaQueryWrapper<MessageDO>()
                .eq(MessageDO::getDeviceId, deviceId)
                .eq(MessageDO::getRoleId, roleId), time);
    }

    @Override
    public List<MessageBO> listSessionHistoryAfter(String sessionId, Instant time) {
        if (!StringUtils.hasText(sessionId) || time == null) {
            return Collections.emptyList();
        }
        return latestAfter(new LambdaQueryWrapper<MessageDO>()
                .eq(MessageDO::getSessionId, sessionId), time);
    }

    /**
     * 按时间倒序取最近 N 条再翻正序：积压超限时优先保留离当前对话最近的历史
     */
    private List<MessageBO> latestAfter(LambdaQueryWrapper<MessageDO> filter, Instant time) {
        LocalDateTime createTime = LocalDateTime.ofInstant(time, ZoneId.systemDefault());
        List<MessageBO> desc = messageMapper.selectList(filter
                .eq(MessageDO::getState, MessageBO.STATE_ENABLED)
                .gt(MessageDO::getCreateTime, createTime)
                .orderByDesc(MessageDO::getCreateTime)
                .orderByDesc(MessageDO::getMessageId)
                .last("LIMIT " + MAX_HISTORY_AFTER_ROWS))
            .stream()
            .map(messageConvert::toBO)
            .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(desc);
        return desc;
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
        MessageDO messageDO = findAssistantMessage(deviceId, roleId, createTime);
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

    @Override
    @Transactional
    public void truncateAssistant(String deviceId, Integer roleId, LocalDateTime createTime, String spokenText) {
        if (!StringUtils.hasText(deviceId) || roleId == null || createTime == null) {
            return;
        }
        MessageDO messageDO = findAssistantMessage(deviceId, roleId, createTime);
        if (messageDO == null) {
            return;
        }
        if (!StringUtils.hasText(spokenText)) {
            messageMapper.deleteById(messageDO.getMessageId());
            return;
        }
        LambdaUpdateWrapper<MessageDO> update = new LambdaUpdateWrapper<MessageDO>()
            .eq(MessageDO::getMessageId, messageDO.getMessageId())
            .set(MessageDO::getMessage, spokenText)
            .set(MessageDO::getUpdateTime, LocalDateTime.now());
        messageMapper.update(null, update);
    }

    /**
     * 按设备、角色与创建时间定位一条正常的 assistant 消息，只取 messageId。
     */
    private MessageDO findAssistantMessage(String deviceId, Integer roleId, LocalDateTime createTime) {
        return messageMapper.selectOne(new LambdaQueryWrapper<MessageDO>()
            .eq(MessageDO::getDeviceId, deviceId)
            .eq(MessageDO::getRoleId, roleId)
            .eq(MessageDO::getSender, MessageBO.SENDER_ASSISTANT)
            .eq(MessageDO::getMessageType, MessageBO.MESSAGE_TYPE_NORMAL)
            .eq(MessageDO::getCreateTime, createTime)
            .select(MessageDO::getMessageId));
    }

    @Override
    public int purgeExpiredAudio(int retentionDays, int batchSize) {
        LocalDateTime expireBefore = LocalDateTime.now().minusDays(retentionDays);
        int purged = 0;

        while (true) {
            List<MessageDO> batch = messageMapper.selectList(new LambdaQueryWrapper<MessageDO>()
                .select(MessageDO::getMessageId, MessageDO::getAudioPath)
                .isNotNull(MessageDO::getAudioPath)
                .ne(MessageDO::getAudioPath, "")
                .lt(MessageDO::getCreateTime, expireBefore)
                .orderByAsc(MessageDO::getMessageId)
                .last("LIMIT " + batchSize));
            if (batch.isEmpty()) {
                break;
            }

            List<Long> messageIds = new ArrayList<>(batch.size());
            for (MessageDO message : batch) {
                // 按每行自己的路径形态删：换过存储之后，同一批里可能既有本地文件又有云对象
                storageServiceFactory.removeFrom(message.getAudioPath());
                messageIds.add(message.getMessageId());
            }

            // 置空而不是删行：录音过期了，对话文本还要留着。
            // 置空后下一轮查询不会再选中这批，循环得以收敛。
            messageMapper.update(null, new LambdaUpdateWrapper<MessageDO>()
                .set(MessageDO::getAudioPath, null)
                .in(MessageDO::getMessageId, messageIds));
            purged += messageIds.size();
        }
        return purged;
    }

    @Override
    public long countStoredPathsWithPrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return 0;
        }
        return messageMapper.selectCount(new LambdaQueryWrapper<MessageDO>()
            .eq(MessageDO::getState, MessageBO.STATE_ENABLED)
            .likeRight(MessageDO::getAudioPath, prefix));
    }
}
