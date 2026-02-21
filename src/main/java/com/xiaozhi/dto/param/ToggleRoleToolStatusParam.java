package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 切换角色工具状态请求参数
 *
 * @author Joey
 */
@Data
@Schema(description = "切换角色工具状态请求参数")
public class ToggleRoleToolStatusParam {

    @Schema(description = "角色ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "角色ID不能为空")
    private Integer roleId;

    @Schema(description = "工具名称", example = "get_weather", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "工具名称不能为空")
    private String toolName;

    @Schema(description = "服务器名称", example = "weather_server", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "服务器名称不能为空")
    private String serverName;

    @Schema(description = "是否启用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}
