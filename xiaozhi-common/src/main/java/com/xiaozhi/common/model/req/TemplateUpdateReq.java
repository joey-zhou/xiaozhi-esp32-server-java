package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "提示词模板更新请求")
public class TemplateUpdateReq {

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "模板描述")
    private String templateDesc;

    @Schema(description = "模板内容")
    private String templateContent;

    @Schema(description = "模板分类")
    private String category;

    @Schema(description = "是否默认模板(1是 0否)")
    private String isDefault;

    @Schema(description = "状态(1启用 0禁用)")
    private String state;
}
