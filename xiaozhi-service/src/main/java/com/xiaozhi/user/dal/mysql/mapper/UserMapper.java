package com.xiaozhi.user.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.user.dal.mysql.dataobject.UserDO;
import com.xiaozhi.user.model.UserProjection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {

    IPage<UserProjection> selectPage(Page<UserProjection> page,
                                     @Param("name") String name,
                                     @Param("email") String email,
                                     @Param("tel") String tel,
                                     @Param("isAdmin") String isAdmin,
                                     @Param("authRoleId") Integer authRoleId);
}
