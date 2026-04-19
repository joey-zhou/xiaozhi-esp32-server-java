package com.xiaozhi.common.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "配置信息")
public class ConfigResp {

    @Schema(description = "配置ID")
    private Integer configId;

    @Schema(description = "用户ID")
    private Integer userId;

    @Schema(description = "配置名称")
    private String configName;

    @Schema(description = "配置描述")
    private String configDesc;

    @Schema(description = "配置类型")
    private String configType;

    @Schema(description = "模型类型")
    private String modelType;

    @Schema(description = "服务提供商")
    private String provider;

    @Schema(description = "服务提供商分配的AppId")
    private String appId;

    @Schema(description = "服务提供商的API地址")
    private String apiUrl;

    @Schema(description = "状态(1启用 0禁用)")
    private String state;

    @Schema(description = "是否默认配置(1是 0否)")
    private String isDefault;

    @Schema(description = "是否启用思考模式")
    private Boolean enableThinking;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
