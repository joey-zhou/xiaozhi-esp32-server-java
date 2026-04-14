package com.xiaozhi.userauth.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaozhi.userauth.dal.mysql.dataobject.UserAuthDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAuthMapper extends BaseMapper<UserAuthDO> {
}
