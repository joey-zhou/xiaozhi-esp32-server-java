package com.xiaozhi.authrole.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaozhi.authrole.dal.mysql.dataobject.AuthRoleDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthRoleMapper extends BaseMapper<AuthRoleDO> {
}
