package com.xiaozhi.dao;

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

  int delete(SysMessage message);

  List<SysMessage> query(SysMessage message);
}