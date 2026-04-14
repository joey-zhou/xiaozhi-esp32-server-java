package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "提示词模板创建请求")
public class TemplateCreateReq {

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    @Schema(description = "模板描述")
    private String templateDesc;

    @Schema(description = "模板内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板内容不能为空")
    private String templateContent;

    @Schema(description = "模板分类", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板分类不能为空")
    private String category;

    @Schema(description = "是否默认模板(1是 0否)")
    private String isDefault;

    @Schema(description = "状态(1启用 0禁用)")
    private String state;
}
