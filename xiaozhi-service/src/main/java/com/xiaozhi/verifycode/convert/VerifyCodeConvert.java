package com.xiaozhi.verifycode.convert;

import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.verifycode.dal.mysql.dataobject.VerifyCodeDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VerifyCodeConvert {

    VerifyCodeBO toBO(VerifyCodeDO verifyCodeDO);
}
