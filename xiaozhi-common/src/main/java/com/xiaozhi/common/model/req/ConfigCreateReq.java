package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "配置创建请求")
public class ConfigCreateReq {

    @Schema(description = "配置名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "配置名称不能为空")
    private String configName;

    @Schema(description = "配置描述")
    private String configDesc;

    @Schema(description = "配置类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "配置类型不能为空")
    private String configType;

    @Schema(description = "模型类型")
    private String modelType;

    @Schema(description = "服务提供商", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "服务提供商不能为空")
    private String provider;

    @Schema(description = "服务提供商分配的AppId")
    private String appId;

    @Schema(description = "服务提供商分配的ApiKey")
    private String apiKey;

    @Schema(description = "服务提供商分配的ApiSecret")
    private String apiSecret;

    @Schema(description = "服务提供商分配的Access Key")
    private String ak;

    @Schema(description = "服务提供商分配的Secret Key")
    private String sk;

    @Schema(description = "服务提供商的API地址")
    private String apiUrl;

    @Schema(description = "状态(1启用 0禁用)")
    private String state;

    @Schema(description = "是否默认配置(1是 0否)")
    private String isDefault;

    @Schema(description = "是否启用思考模式(模型支持时生效)")
    private Boolean enableThinking;
}
