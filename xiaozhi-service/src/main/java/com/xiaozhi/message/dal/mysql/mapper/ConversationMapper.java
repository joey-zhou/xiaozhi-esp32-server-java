package com.xiaozhi.message.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.message.model.ConversationProjection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConversationMapper {

    IPage<ConversationProjection> selectConversationPage(Page<ConversationProjection> page,
                                                         @Param("userId") Integer userId,
                                                         @Param("roleId") Integer roleId,
                                                         @Param("source") String source);
}
