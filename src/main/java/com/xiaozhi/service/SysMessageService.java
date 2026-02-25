package com.xiaozhi.service;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysMessage;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 聊天记录查询/添加
 * 
 * @author Joey
 * 
 */
public interface SysMessageService {

  /**
   * 新增记录
   * 
   * @param message
   * @return
   */
  int add(SysMessage message);

  @Transactional
  int saveAll(List<SysMessage> messages);

  /**
   * 查询聊天记录
   * 指定分页信息
   * @param message
   * @return
   */
  List<SysMessage> query(SysMessage message, PageFilter pageFilter);

  /**
   * 根据消息ID查询消息
   */
  SysMessage findById(Integer messageId);

  /**
   * 删除记忆
   * 
   * @param message
   * @return
   */
  int delete(SysMessage message);

  /**
   * 更新消息的音频数据信息
   * @param deviceId
   * @param roleId
   * @param sender
   * @param createTime
   * @param audioPath
   */
  void updateMessageByAudioFile(String deviceId, Integer roleId, String sender, String createTime,
                                String audioPath);
}