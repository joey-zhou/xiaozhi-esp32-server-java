package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "提示词模板分页查询")
public class TemplatePageReq extends BasePageReq {

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "模板分类")
    private String category;
}
