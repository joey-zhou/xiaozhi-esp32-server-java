package com.xiaozhi.template.service;

import com.xiaozhi.common.model.bo.TemplateBO;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.model.resp.TemplateResp;

import java.util.List;

public interface TemplateService {

    PageResp<TemplateResp> page(int pageNo, int pageSize, String templateName, String category, Integer userId);

    TemplateResp get(Integer templateId);

    TemplateBO getBO(Integer templateId);

    List<TemplateBO> listBO(Integer userId, String templateName, String category);
}
