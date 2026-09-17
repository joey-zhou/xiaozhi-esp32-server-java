package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "会话分页查询")
public class ConversationPageReq extends BasePageReq {

    @Schema(description = "角色ID，不传时不限角色")
    private Integer roleId;
}
