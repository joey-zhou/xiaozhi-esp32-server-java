package com.xiaozhi.common.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页响应")
public class PageResult<T> implements Serializable {

    @Schema(description = "数据列表")
    private List<T> list;

    @Schema(description = "总记录数")
    private Long total;

    @Schema(description = "页码")
    private Integer pageNo;

    @Schema(description = "每页数量")
    private Integer pageSize;

    /** 换元素类型、保留分页元信息。读侧返回 BO，出参组装时在 server 侧一次性换成 Resp。 */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(
            list == null ? List.of() : list.stream().map(mapper).map(r -> (R) r).toList(),
            total, pageNo, pageSize);
    }
}
