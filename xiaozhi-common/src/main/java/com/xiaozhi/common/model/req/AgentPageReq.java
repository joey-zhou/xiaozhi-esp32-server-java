package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "智能体分页查询")
public class AgentPageReq extends BasePageReq {

    @Schema(description = "服务提供商")
    private String provider;

    @Schema(description = "智能体名称")
    private String agentName;
}
