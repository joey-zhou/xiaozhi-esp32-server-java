package com.xiaozhi.service.impl;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.repository.SysMessageRepository;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.utils.AudioUtils;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 聊天记录
 *
 * @author Joey
 *
 */

@Service
public class SysMessageServiceImpl extends BaseServiceImpl implements SysMessageService {

    @Resource
    private SysMessageRepository sysMessageRepository;

    @Resource
    private SessionManager sessionManager;

    /**
     * 新增聊天记录
     *
     * @param message
     * @return
     */
    @Override
    @Transactional
    public int add(SysMessage message) {
        sysMessageRepository.save(message);
        return 1;
    }

    @Override
    @Transactional
    public int saveAll(List<SysMessage> messages) {
        sysMessageRepository.saveAll(messages);
        return messages.size();
    }

    /**
     * 查询聊天记录
     *
     * @param message
     * @return
     */
    @Override
    public List<SysMessage> query(SysMessage message, PageFilter pageFilter) {
        if (pageFilter != null) {
            Page<SysMessage> page = sysMessageRepository.findMessages(
                    message.getDeviceId(),
                    message.getSender(),
                    message.getRoleId(),
                    message.getMessageType(),
                    PageRequest.of(pageFilter.getStart() - 1, pageFilter.getLimit(), Sort.by(Sort.Direction.DESC, "createTime"))
            );
            return page.getContent();
        }
        return sysMessageRepository.findMessages(
                message.getDeviceId(),
                message.getSender(),
                message.getRoleId(),
                message.getMessageType(),
                PageRequest.of(0, 10)
        ).getContent();
    }

    @Override
    public SysMessage findById(Integer messageId) {
        return sysMessageRepository.findByIdAndActive(messageId).orElse(null);
    }

    /**
     * 删除记忆，同步删除关联的音频文件
     * - 单条删除（messageId 不为空）：直接从记录中拿 audioPath 删文件
     * - 批量删除（deviceId 不为空）：遍历最近 RETENTION_DAYS 天的日期目录，删除对应 device 子目录
     *
     * @param message
     * @return
     */
    @Override
    @Transactional
    public int delete(SysMessage message) {
        if (message.getMessageId() != null) {
            // 单条：直接拿 audioPath 删文件
            SysMessage existing = sysMessageRepository.findByIdAndActive(message.getMessageId()).orElse(null);
            if (existing != null && StringUtils.hasText(existing.getAudioPath())) {
                AudioUtils.deleteFile(existing.getAudioPath());
            }
        } else if (StringUtils.hasText(message.getDeviceId())) {
            // 批量：遍历最近 RETENTION_DAYS 天的日期目录，删除 device 子目录
            String deviceId = message.getDeviceId().replace(":", "-");
            LocalDate today = LocalDate.now();
            for (int i = 0; i <= AudioUtils.AUDIO_RETENTION_DAYS; i++) {
                String date = today.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
                Path deviceDir = Path.of(AudioUtils.AUDIO_PATH, date, deviceId);
                AudioUtils.deleteDirectory(deviceDir);
            }
            // 清除内存中的对话历史，避免数据库已清空但 LLM 仍能看到旧上下文
            sessionManager.findConversation(message.getDeviceId()).ifPresent(conversation -> conversation.clear());
        }

        return sysMessageRepository.deleteByDeviceAndUser(message.getDeviceId(), message.getUserId());
    }

    @Override
    @Transactional
    public void updateMessageByAudioFile(String deviceId, Integer roleId, String sender,
                                         String createTime, String audioPath) {
        sysMessageRepository.updateAudioPath(deviceId, roleId, sender, createTime, audioPath);
    }

}
