package com.xiaozhi.userauth.convert;

import com.xiaozhi.common.model.bo.UserAuthBO;
import com.xiaozhi.userauth.dal.mysql.dataobject.UserAuthDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserAuthConvert {

    UserAuthBO toBO(UserAuthDO d);

    UserAuthDO toDO(UserAuthBO bo);
}
