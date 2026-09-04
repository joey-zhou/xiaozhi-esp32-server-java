package com.xiaozhi.verifycode.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaozhi.verifycode.dal.mysql.dataobject.VerifyCodeDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VerifyCodeMapper extends BaseMapper<VerifyCodeDO> {
}
