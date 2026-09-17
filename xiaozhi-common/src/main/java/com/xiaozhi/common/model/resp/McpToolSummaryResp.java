package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP 工具摘要")
public class McpToolSummaryResp {

    @Schema(description = "工具名称")
    private String name;

    @Schema(description = "工具描述")
    private String description;
}
