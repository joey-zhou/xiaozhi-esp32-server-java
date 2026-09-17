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

    /**
     * 会话总数：不带 title 相关子查询与 sys_role 关联的轻量统计，配合
     * {@link com.baomidou.mybatisplus.extension.plugins.pagination.Page#setSearchCount(boolean)} = false 使用，
     * 避免 MyBatis-Plus 自动 COUNT 把 GROUP BY + 相关子查询整段重跑一次。
     */
    long selectConversationCount(@Param("userId") Integer userId,
                                 @Param("roleId") Integer roleId,
                                 @Param("source") String source);
}
