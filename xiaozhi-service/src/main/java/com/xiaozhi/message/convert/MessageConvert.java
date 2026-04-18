package com.xiaozhi.message.convert;

import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.bo.MessageMetadataBO;
import com.xiaozhi.common.model.resp.MessageResp;
import com.xiaozhi.message.dal.mysql.dataobject.MessageDO;
import com.xiaozhi.utils.JsonUtil;
import org.mapstruct.Mapper;
import org.springframework.util.StringUtils;

@Mapper(componentModel = "spring")
public interface MessageConvert {

    MessageBO toBO(MessageDO messageDO);

    MessageDO toDO(MessageBO messageBO);

    MessageResp toResp(MessageBO messageBO);

    /**
     * DO.metadata (JSON 字符串) → BO.metadata (值对象)。
     * MapStruct 自动识别方法签名并用于 toBO 映射。
     */
    default MessageMetadataBO jsonToMetadata(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        return JsonUtil.fromJson(json, MessageMetadataBO.class);
    }

    /**
     * BO.metadata (值对象) → DO.metadata (JSON 字符串)。
     * MapStruct 自动识别方法签名并用于 toDO 映射。
     */
    default String metadataToJson(MessageMetadataBO metadata) {
        if (metadata == null) {
            return null;
        }
        return JsonUtil.toJson(metadata);
    }
}
