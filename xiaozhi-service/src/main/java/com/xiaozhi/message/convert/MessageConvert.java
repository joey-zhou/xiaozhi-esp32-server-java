package com.xiaozhi.message.convert;

import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.resp.MessageResp;
import com.xiaozhi.message.dal.mysql.dataobject.MessageDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MessageConvert {

    MessageBO toBO(MessageDO messageDO);

    MessageDO toDO(MessageBO messageBO);

    MessageResp toResp(MessageBO messageBO);
}
