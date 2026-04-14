package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量设置角色排除工具请求")
public class McpRoleExcludeToolsReq {

    @Schema(description = "排除的工具列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "排除工具列表不能为空")
    private List<String> excludeTools;

    @Schema(description = "服务器名称")
    private String serverName;
}
