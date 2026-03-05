package com.xiaozhi.common.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 分页数据封装类，用于将 JPA Page 转换为前端期望的格式
 *
 * @param <T> 数据类型
 * @author Joey
 */
public class PageData<T> {

    /** 数据列表 */
    private List<T> list;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private int pageNum;

    /** 每页数量 */
    private int pageSize;

    /** 当前页数量 */
    private int size;

    /** 起始行号（从 1 开始） */
    private int startRow;

    /** 结束行号 */
    private int endRow;

    /** 总页数 */
    private int pages;

    /** 上一页（页码，从 1 开始，没有则为 0） */
    private int prePage;

    /** 下一页（页码，从 1 开始，没有则为 0） */
    private int nextPage;

    /** 是否第一页 */
    private boolean isFirstPage;

    /** 是否最后一页 */
    private boolean isLastPage;

    /** 是否有上一页 */
    private boolean hasPreviousPage;

    /** 是否有下一页 */
    private boolean hasNextPage;

    /** 导航页码数 */
    private int navigatePages;

    /** 导航页码列表 */
    private int[] navigatepageNums;

    /** 导航页码第一个 */
    private int navigateFirstPage;

    /** 导航页码最后一个 */
    private int navigateLastPage;

    public PageData() {
    }

    /**
     * 从 JPA Page 构建 PageData
     *
     * @param page JPA Page 对象
     */
    public PageData(Page<T> page) {
        this.list = page.getContent();
        this.total = page.getTotalElements();
        this.pageNum = page.getNumber() + 1; // JPA 从 0 开始，前端从 1 开始
        this.pageSize = page.getSize();
        this.size = page.getNumberOfElements();
        
        // 计算起始行和结束行
        if (page.getTotalElements() == 0) {
            this.startRow = 0;
            this.endRow = 0;
        } else {
            this.startRow = page.getNumber() * page.getSize() + 1;
            this.endRow = page.getNumber() * page.getSize() + page.getNumberOfElements();
        }
        
        this.pages = page.getTotalPages();
        this.prePage = page.hasPrevious() ? page.getNumber() : 0;
        this.nextPage = page.hasNext() ? page.getNumber() + 2 : 0; // 前端从 1 开始
        this.isFirstPage = page.isFirst();
        this.isLastPage = page.isLast();
        this.hasPreviousPage = page.hasPrevious();
        this.hasNextPage = page.hasNext();
        this.navigatePages = 8;
        this.navigatepageNums = calculateNavigatePages(page.getNumber() + 1, page.getTotalPages());
        this.navigateFirstPage = this.navigatepageNums.length > 0 ? this.navigatepageNums[0] : 0;
        this.navigateLastPage = this.navigatepageNums.length > 0 ? this.navigatepageNums[this.navigatepageNums.length - 1] : 0;
    }

    /**
     * 从 JPA Page 构建 PageData（静态工厂方法）
     *
     * @param page JPA Page 对象
     * @return PageData 对象
     */
    public static <T> PageData<T> of(Page<T> page) {
        return new PageData<>(page);
    }

    /**
     * 手动构建分页数据（用于非 JPA Page 场景）
     */
    public static <T> PageData<T> of(List<T> list, long total, int pageNum, int pageSize) {
        PageData<T> pageData = new PageData<>();
        pageData.list = list;
        pageData.total = total;
        pageData.pageNum = pageNum;
        pageData.pageSize = pageSize;
        pageData.size = list.size();
        
        if (total == 0) {
            pageData.startRow = 0;
            pageData.endRow = 0;
            pageData.pages = 0;
        } else {
            pageData.startRow = (pageNum - 1) * pageSize + 1;
            pageData.endRow = Math.min((int) total, pageNum * pageSize);
            pageData.pages = (int) Math.ceil((double) total / pageSize);
        }
        
        pageData.prePage = pageNum > 1 ? pageNum - 1 : 0;
        pageData.nextPage = pageNum < pageData.pages ? pageNum + 1 : 0;
        pageData.isFirstPage = pageNum == 1;
        pageData.isLastPage = pageNum == pageData.pages;
        pageData.hasPreviousPage = pageNum > 1;
        pageData.hasNextPage = pageNum < pageData.pages;
        pageData.navigatePages = 8;
        pageData.navigatepageNums = calculateNavigatePagesStatic(pageNum, pageData.pages, 8);
        pageData.navigateFirstPage = pageData.navigatepageNums.length > 0 ? pageData.navigatepageNums[0] : 0;
        pageData.navigateLastPage = pageData.navigatepageNums.length > 0 ? pageData.navigatepageNums[pageData.navigatepageNums.length - 1] : 0;
        
        return pageData;
    }

    /**
     * 构建空的分页数据
     *
     * @return 空的 PageData 对象
     */
    public static <T> PageData<T> empty() {
        PageData<T> pageData = new PageData<>();
        pageData.list = List.of();
        pageData.total = 0;
        pageData.pageNum = 1;
        pageData.pageSize = 10;
        pageData.size = 0;
        pageData.startRow = 0;
        pageData.endRow = 0;
        pageData.pages = 0;
        pageData.prePage = 0;
        pageData.nextPage = 0;
        pageData.isFirstPage = true;
        pageData.isLastPage = true;
        pageData.hasPreviousPage = false;
        pageData.hasNextPage = false;
        pageData.navigatePages = 8;
        pageData.navigatepageNums = new int[0];
        pageData.navigateFirstPage = 0;
        pageData.navigateLastPage = 0;
        return pageData;
    }

    /**
     * 计算导航页码数组
     *
     * @param pageNum 当前页码（从 1 开始）
     * @param pages   总页数
     * @return 导航页码数组
     */
    private int[] calculateNavigatePages(int pageNum, int pages) {
        return calculateNavigatePagesStatic(pageNum, pages, this.navigatePages);
    }

    /**
     * 静态方法计算导航页码数组
     */
    private static int[] calculateNavigatePagesStatic(int pageNum, int pages, int navigatePages) {
        if (pages <= 0) {
            return new int[0];
        }
        if (pages <= navigatePages) {
            int[] result = new int[pages];
            for (int i = 0; i < pages; i++) {
                result[i] = i + 1;
            }
            return result;
        }

        int[] result = new int[navigatePages];
        int startNum = pageNum - navigatePages / 2;
        int endNum = pageNum + navigatePages / 2;

        if (startNum < 1) {
            startNum = 1;
            endNum = navigatePages;
        }

        if (endNum > pages) {
            endNum = pages;
            startNum = pages - navigatePages + 1;
        }

        for (int i = 0; i < navigatePages; i++) {
            result[i] = startNum + i;
        }

        return result;
    }

    // Getters and Setters

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getStartRow() {
        return startRow;
    }

    public void setStartRow(int startRow) {
        this.startRow = startRow;
    }

    public int getEndRow() {
        return endRow;
    }

    public void setEndRow(int endRow) {
        this.endRow = endRow;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public int getPrePage() {
        return prePage;
    }

    public void setPrePage(int prePage) {
        this.prePage = prePage;
    }

    public int getNextPage() {
        return nextPage;
    }

    public void setNextPage(int nextPage) {
        this.nextPage = nextPage;
    }

    public boolean isFirstPage() {
        return isFirstPage;
    }

    public void setFirstPage(boolean firstPage) {
        isFirstPage = firstPage;
    }

    public boolean isLastPage() {
        return isLastPage;
    }

    public void setLastPage(boolean lastPage) {
        isLastPage = lastPage;
    }

    public boolean hasPreviousPage() {
        return hasPreviousPage;
    }

    public void setHasPreviousPage(boolean hasPreviousPage) {
        this.hasPreviousPage = hasPreviousPage;
    }

    public boolean hasNextPage() {
        return hasNextPage;
    }

    public void setHasNextPage(boolean hasNextPage) {
        this.hasNextPage = hasNextPage;
    }

    public int getNavigatePages() {
        return navigatePages;
    }

    public void setNavigatePages(int navigatePages) {
        this.navigatePages = navigatePages;
    }

    public int[] getNavigatepageNums() {
        return navigatepageNums;
    }

    public void setNavigatepageNums(int[] navigatepageNums) {
        this.navigatepageNums = navigatepageNums;
    }

    public int getNavigateFirstPage() {
        return navigateFirstPage;
    }

    public void setNavigateFirstPage(int navigateFirstPage) {
        this.navigateFirstPage = navigateFirstPage;
    }

    public int getNavigateLastPage() {
        return navigateLastPage;
    }

    public void setNavigateLastPage(int navigateLastPage) {
        this.navigateLastPage = navigateLastPage;
    }
}
