package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "批量更新设备请求")
public class DeviceBatchUpdateReq {

    @NotBlank(message = "设备ID不能为空")
    @Schema(description = "设备ID列表，以逗号分隔", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceIds;

    @Schema(description = "角色ID")
    private Integer roleId;
}
