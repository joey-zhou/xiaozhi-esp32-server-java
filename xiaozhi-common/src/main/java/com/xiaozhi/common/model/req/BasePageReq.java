package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "分页请求基类")
public abstract class BasePageReq implements Serializable {

    @Schema(description = "页码", example = "1")
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNo = 1;

    @Schema(description = "每页数量", example = "10")
    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 1000, message = "每页数量最大为1000")
    private Integer pageSize = 10;
}
