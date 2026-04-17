package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "会话响应")
public class ConversationResp {

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "角色ID")
    private Integer roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "会话标题（第一条消息内容）")
    private String title;

    @Schema(description = "最近更新时间")
    private Date updateTime;
}
