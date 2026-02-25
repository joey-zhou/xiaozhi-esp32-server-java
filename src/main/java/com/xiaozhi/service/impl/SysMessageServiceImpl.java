package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dao.MessageMapper;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.DateUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private MessageMapper messageMapper;

    /**
     * 新增聊天记录
     *
     * @param message
     * @return
     */
    @Override
    @Transactional
    public int add(SysMessage message) {
        return messageMapper.add(message);
    }

    @Override
    @Transactional
    public int saveAll(List<SysMessage> messages) {
        return messageMapper.saveAll(messages);
    }

    /**
     * 查询聊天记录
     *
     * @param message
     * @return
     */
    @Override
    public List<SysMessage> query(SysMessage message, PageFilter pageFilter) {
        if(pageFilter != null){
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        return messageMapper.query(message);
    }

    @Override
    public SysMessage findById(Integer messageId) {
        return messageMapper.findById(messageId);
    }

    /**
     * 删除记忆，同步删除关联的音频文件
     * - 单条删除（messageId不为空）：直接从记录中拿 audioPath 删文件
     * - 批量删除（deviceId不为空）：遍历最近 RETENTION_DAYS 天的日期目录，删除对应 device 子目录
     *
     * @param message
     * @return
     */
    @Override
    @Transactional
    public int delete(SysMessage message) {
        if (message.getMessageId() != null) {
            // 单条：直接拿 audioPath 删文件
            SysMessage existing = messageMapper.findById(message.getMessageId());
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
        }

        return messageMapper.delete(message);
    }

    @Override
    public void updateMessageByAudioFile(String deviceId, Integer roleId, String sender,
                                         String createTime, String audioPath) {
        SysMessage sysMessage = new SysMessage();
        // 设置消息的where条件
        sysMessage.setDeviceId(deviceId);
        sysMessage.setRoleId(roleId);
        sysMessage.setSender(sender);
        sysMessage.setCreateTime(DateUtils.toDate(createTime.replace("T", " "), "yyyy-MM-dd HHmmss"));
        // 设置音频路径和TTS时长
        sysMessage.setAudioPath(audioPath);
        messageMapper.updateMessageByAudioFile(sysMessage);
    }

}