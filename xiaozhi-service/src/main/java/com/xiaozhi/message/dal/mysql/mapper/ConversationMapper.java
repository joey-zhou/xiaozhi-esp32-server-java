package com.xiaozhi.message.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.model.resp.ConversationResp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConversationMapper {

    IPage<ConversationResp> selectConversationPage(Page<ConversationResp> page,
                                                   @Param("userId") Integer userId,
                                                   @Param("roleId") Integer roleId,
                                                   @Param("source") String source);
}
