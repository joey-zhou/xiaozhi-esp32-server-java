package com.xiaozhi.service;

import java.util.List;
import com.xiaozhi.entity.SysMessage;

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
  public int add(SysMessage message);

  /**
   * 查询聊天记录
   * 
   * @param message
   * @return
   */
  public List<SysMessage> query(SysMessage message);

  /**
   * 删除记忆
   * 
   * @param message
   * @return
   */
  public int delete(SysMessage message);

}