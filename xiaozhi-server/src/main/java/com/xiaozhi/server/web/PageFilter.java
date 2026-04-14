package com.xiaozhi.server.web;

/**
 * 分页信息
 */
public class PageFilter {
    private Integer start = 1;

    private Integer limit = 10;

    public PageFilter() {
    }

    public PageFilter(Integer start, Integer limit) {
        this.start = start;
        this.limit = limit;
    }

    public Integer getStart() {
        return start;
    }

    public void setStart(Integer start) {
        this.start = start;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
