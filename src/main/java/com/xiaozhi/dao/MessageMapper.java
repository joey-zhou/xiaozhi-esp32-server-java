package com.xiaozhi.dao;

import java.time.Instant;
import java.util.List;

import com.xiaozhi.entity.SysMessage;

/**
 * 聊天记录 数据层
 * 
 * @author Joey
 * 
 */
public interface MessageMapper {

  int add(SysMessage message);

  void saveAll(List<SysMessage> messages);

  int delete(SysMessage message);

  List<SysMessage> query(SysMessage message);

  List<SysMessage> find(String deviceId, int roleId, int  limit);

  List<SysMessage> findAfter(String deviceId, int roleId, Instant timeMillis);

    /**
     * 更新消息的音频数据信息
     *
     * @param sysMessage
     */
  void updateMessageByAudioFile(SysMessage sysMessage);
}