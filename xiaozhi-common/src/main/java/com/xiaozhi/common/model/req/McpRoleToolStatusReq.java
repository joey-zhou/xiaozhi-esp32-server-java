package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "切换角色 MCP 工具状态请求")
public class McpRoleToolStatusReq {

    @Schema(description = "工具名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "工具名称不能为空")
    private String toolName;

    @Schema(description = "服务器名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "服务器名称不能为空")
    private String serverName;

    @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}
