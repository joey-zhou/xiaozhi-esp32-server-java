package com.xiaozhi.message.convert;

import com.xiaozhi.common.model.bo.ConversationBO;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.bo.MessageMetadataBO;
import com.xiaozhi.common.model.resp.ConversationResp;
import com.xiaozhi.common.model.resp.MessageAttachmentResp;
import com.xiaozhi.common.model.resp.MessageResp;
import com.xiaozhi.message.dal.mysql.dataobject.ConversationDO;
import com.xiaozhi.message.dal.mysql.dataobject.MessageDO;
import com.xiaozhi.message.model.MessageProjection;
import com.xiaozhi.utils.JsonUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.util.StringUtils;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MessageConvert {

    MessageBO toBO(MessageDO messageDO);

    MessageDO toDO(MessageBO messageBO);

    @Mapping(target = "attachments", source = "metadata")
    MessageResp toResp(MessageProjection projection);

    /** 角色名只在分页 SQL 里关联出来，单表读不带 */
    @Mapping(target = "roleName", ignore = true)
    ConversationBO toBO(ConversationDO conversation);

    ConversationResp toResp(ConversationBO conversation);

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

    /** metadata 里的附件，没有为 null */
    default List<MessageAttachmentResp> jsonToAttachments(String json) {
        MessageMetadataBO metadata = jsonToMetadata(json);
        if (metadata == null || metadata.getAttachments() == null) {
            return null;
        }
        return metadata.getAttachments().stream().map(attachment -> {
            MessageAttachmentResp resp = new MessageAttachmentResp();
            resp.setKind(attachment.getKind());
            resp.setName(attachment.getName());
            resp.setUrl(attachment.getPath());
            return resp;
        }).toList();
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
