package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "会话分页查询")
public class ConversationPageReq extends BasePageReq {

    @Schema(description = "角色ID")
    private Integer roleId;

    @Schema(description = "消息来源: web|device")
    private String source;
}
