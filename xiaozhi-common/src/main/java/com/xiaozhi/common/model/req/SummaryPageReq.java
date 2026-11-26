package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "对话摘要分页查询")
public class SummaryPageReq extends BasePageReq {

    @Schema(description = "设备ID，不传查当前用户全部设备")
    private String deviceId;

    @Schema(description = "角色ID，不传不限角色")
    private Integer roleId;
}
