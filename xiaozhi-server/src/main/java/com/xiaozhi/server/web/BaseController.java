package com.xiaozhi.server.web;

import com.xiaozhi.server.web.PageFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * @description: 基础控制器
 *
 * @author Joey
 *
 */
public class BaseController {

    /**
     * 最大分页数量
     */
    public static final int MAX_PAGE_SIZE = 1000;

    protected PageFilter initPageFilter(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String pageNo = request.getParameter("pageNo");
        String pageSize = request.getParameter("pageSize");
        if (!StringUtils.hasText(pageNo) && !StringUtils.hasText(pageSize)) {
            return null;
        }

        PageFilter pageFilter = new PageFilter();
        if (StringUtils.hasText(pageNo)) {
            pageFilter.setStart(Math.max(Integer.parseInt(pageNo), 1));
        }
        if (StringUtils.hasText(pageSize)) {
            int pageSizeValue = Math.min(Math.max(Integer.parseInt(pageSize), 1), MAX_PAGE_SIZE);
            pageFilter.setLimit(pageSizeValue);
        }
        return pageFilter;
    }
}
