package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "配置分页查询")
public class ConfigPageReq extends BasePageReq {

    @Schema(description = "配置类型")
    private String configType;

    @Schema(description = "配置名称")
    private String configName;

    @Schema(description = "模型类型")
    private String modelType;

    @Schema(description = "服务提供商")
    private String provider;

    @Schema(description = "是否默认配置(1是 0否)")
    private String isDefault;

    @Schema(description = "状态(1启用 0禁用)")
    private String state;
}
