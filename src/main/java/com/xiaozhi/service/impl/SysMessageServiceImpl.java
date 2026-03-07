package com.xiaozhi.service.impl;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.repository.SysMessageRepository;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.utils.AudioUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;

/**
 * 聊天记录
 *
 * @author Joey
 */
@Service
public class SysMessageServiceImpl implements SysMessageService {

    @Resource
    private SysMessageRepository messageRepository;

    @Override
    @Transactional
    public int add(SysMessage message) {
        return messageRepository.add(message);
    }

    @Override
    @Transactional
    public int saveAll(List<SysMessage> messages) {
        return messageRepository.saveAll(messages);
    }

    @Override
    public List<SysMessage> query(SysMessage message, PageFilter pageFilter) {
        if (pageFilter != null) {
            return messageRepository.findMessages(
                    message.getDeviceId(),
                    message.getSender(),
                    message.getRoleId(),
                    message.getMessageType(),
                    org.springframework.data.domain.PageRequest.of(pageFilter.getStart() - 1, pageFilter.getLimit())
            ).getContent();
        }
        return messageRepository.findAll();
    }

    @Override
    public SysMessage findById(Integer messageId) {
        return messageRepository.findByMessageId(messageId);
    }

    @Override
    @Transactional
    public int delete(SysMessage message) {
        if (message.getMessageId() != null) {
            SysMessage msg = messageRepository.findByMessageId(message.getMessageId());
            if (msg != null && StringUtils.hasText(msg.getAudioPath())) {
                AudioUtils.deleteFile(msg.getAudioPath());
            }
            return messageRepository.deleteMessage(message);
        } else if (StringUtils.hasText(message.getDeviceId())) {
            int retentionDays = 7;
            LocalDate today = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (int i = 0; i < retentionDays; i++) {
                String dateDir = today.minusDays(i).format(formatter);
                Path audioDir = Path.of("audio", dateDir, message.getDeviceId());
                if (message.getRoleId() != null) {
                    audioDir = audioDir.resolve(message.getRoleId().toString());
                }
                AudioUtils.deleteFile(audioDir.toString());
            }
            messageRepository.deleteByDeviceAndUser(message.getDeviceId(), message.getUserId());
            return 1;
        }
        return 0;
    }

    @Override
    public void updateMessageByAudioFile(String deviceId, Integer roleId, String sender, String createTime,
                                         String audioPath) {
        messageRepository.updateAudioPath(deviceId, roleId, sender, createTime, audioPath);
    }
}
