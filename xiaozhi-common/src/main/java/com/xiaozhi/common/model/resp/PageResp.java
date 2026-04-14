package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页响应")
public class PageResp<T> implements Serializable {

    @Schema(description = "数据列表")
    private List<T> list;

    @Schema(description = "总记录数")
    private Long total;

    @Schema(description = "页码")
    private Integer pageNo;

    @Schema(description = "每页数量")
    private Integer pageSize;
}
