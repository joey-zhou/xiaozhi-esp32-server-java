package com.xiaozhi.role.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.role.dal.mysql.dataobject.RoleDO;
import com.xiaozhi.role.model.RoleProjection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMapper extends BaseMapper<RoleDO> {

    IPage<RoleProjection> selectPage(Page<RoleProjection> page,
                                     @Param("roleId") Integer roleId,
                                     @Param("roleName") String roleName,
                                     @Param("isDefault") String isDefault,
                                     @Param("state") String state,
                                     @Param("userId") Integer userId);
}
