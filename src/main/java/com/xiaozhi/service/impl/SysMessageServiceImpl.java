package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dao.MessageMapper;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.utils.DateUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 删除记忆
     * 
     * @param message
     * @return
     */
    @Override
    @Transactional
    public int delete(SysMessage message) {
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