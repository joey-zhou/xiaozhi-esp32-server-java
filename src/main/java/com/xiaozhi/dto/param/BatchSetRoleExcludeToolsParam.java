package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量设置角色排除工具请求参数
 *
 * @author Joey
 */
@Data
@Schema(description = "批量设置角色排除工具请求参数")
public class BatchSetRoleExcludeToolsParam {

    @Schema(description = "角色ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "角色ID不能为空")
    private Integer roleId;

    @Schema(description = "排除的工具列表", example = "[\"get_weather\", \"search_web\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "排除工具列表不能为空")
    private List<String> excludeTools;

    @Schema(description = "服务器名称(可选)", example = "weather_server")
    private String serverName;
}
