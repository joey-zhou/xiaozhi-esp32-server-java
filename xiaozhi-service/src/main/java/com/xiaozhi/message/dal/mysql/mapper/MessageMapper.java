package com.xiaozhi.message.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.model.resp.MessageResp;
import com.xiaozhi.message.dal.mysql.dataobject.MessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

@Mapper
public interface MessageMapper extends BaseMapper<MessageDO> {

    IPage<MessageResp> selectPageResp(Page<MessageResp> page,
                                      @Param("deviceId") String deviceId,
                                      @Param("deviceName") String deviceName,
                                      @Param("sender") String sender,
                                      @Param("messageType") String messageType,
                                      @Param("roleId") Integer roleId,
                                      @Param("startTime") Date startTime,
                                      @Param("endTime") Date endTime,
                                      @Param("userId") Integer userId,
                                      @Param("sessionId") String sessionId,
                                      @Param("source") String source);
}
