package com.xiaozhi.message.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.model.bo.ConversationBO;
import com.xiaozhi.message.dal.mysql.dataobject.ConversationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConversationMapper extends BaseMapper<ConversationDO> {

    /** 当前用户的会话按最后对话时间倒序，带角色名；roleId 为空时不限角色 */
    IPage<ConversationBO> selectPage(Page<ConversationBO> page,
                                     @Param("userId") Integer userId,
                                     @Param("roleId") Integer roleId);
}
