package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "角色与全局禁用的工具列表")
public class McpDisabledToolsResp {

    @Schema(description = "角色级禁用的工具名称")
    private List<String> roleDisabled;

    @Schema(description = "全局禁用的工具名称")
    private List<String> globalDisabled;
}
