package com.xiaozhi.service.impl;

import com.xiaozhi.common.web.PageFilter;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * @description: 基础服务实现类
 */
public class BaseServiceImpl {

    public final static String PAGE_ATTRIBUTE_KEY = "PageFilter";

    /**
     * 获取分页信息
     * @return
     */
    protected PageFilter getPageFilter() {
        PageFilter pageFilter;
        pageFilter = (PageFilter) RequestContextHolder.currentRequestAttributes().
                getAttribute(PAGE_ATTRIBUTE_KEY, RequestAttributes.SCOPE_REQUEST);
        if(pageFilter == null){
            // 某些页面不需要分页，所以不需要添加默认分页数
            // pageFilter = new PageFilter();
            // pageFilter.setStart(0);
            // pageFilter.setLimit(10);
        }
        return pageFilter;
    }
}
