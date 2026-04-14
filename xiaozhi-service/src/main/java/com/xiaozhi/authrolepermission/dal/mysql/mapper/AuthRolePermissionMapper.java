package com.xiaozhi.authrolepermission.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaozhi.authrolepermission.dal.mysql.dataobject.AuthRolePermissionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthRolePermissionMapper extends BaseMapper<AuthRolePermissionDO> {

    int insertBatch(@Param("list") List<AuthRolePermissionDO> list);
}
