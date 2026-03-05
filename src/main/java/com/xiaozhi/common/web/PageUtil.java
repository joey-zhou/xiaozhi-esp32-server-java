package com.xiaozhi.common.web;

import org.springframework.data.domain.Page;

/**
 * 分页工具类，提供便捷的分页数据转换方法
 *
 * @author Joey
 */
public class PageUtil {

    /**
     * 将 JPA Page 转换为前端期望的 PageData 格式
     *
     * @param page JPA Page 对象
     * @return PageData 对象
     */
    public static <T> PageData<T> toPageData(Page<T> page) {
        return new PageData<>(page);
    }

    /**
     * 将 JPA Page 转换为前端期望的 PageData 格式（内容已转换为 DTO）
     *
     * @param page JPA Page 对象
     * @param content DTO 列表
     * @return PageData 对象
     */
    public static <T, D> PageData<D> toPageData(Page<T> page, java.util.List<D> content) {
        return PageData.of(content, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    /**
     * 手动构建分页数据（用于非 JPA 场景）
     *
     * @param list 数据列表
     * @param total 总记录数
     * @param pageNum 当前页码（从 1 开始）
     * @param pageSize 每页数量
     * @return PageData 对象
     */
    public static <T> PageData<T> toPageData(java.util.List<T> list, long total, int pageNum, int pageSize) {
        return PageData.of(list, total, pageNum, pageSize);
    }
}
