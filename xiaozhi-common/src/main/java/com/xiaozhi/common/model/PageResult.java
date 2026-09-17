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

    /**
     * 换元素类型、保留分页元信息。读侧返回 BO，出参组装时在 server 侧一次性换成 Resp。
     * 这里的 <R> 类型见证是必需的：三元表达式两侧（List.of() 与 list.stream().map(mapper)）
     * 的公共类型会退化成 ? extends R 的捕获类型，直接传给 PageResult<>(...) 的钻石构造
     * 无法把捕获类型收回 R，删掉类型见证会导致编译失败。
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(
            list == null ? List.of() : list.stream().<R>map(mapper).toList(),
            total, pageNo, pageSize);
    }
}
