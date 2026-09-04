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

    void copyTemplates(Integer sourceUserId, Integer targetUserId);

    /** 新建模板，返回落库后的 BO（含自增主键，调用方不必回读） */
    TemplateBO create(Integer userId, TemplateBO bo);

    /** 按 BO 里的非空字段增量更新 */
    TemplateBO update(Integer templateId, TemplateBO bo);

    /** 软删：只置 state，不清 isDefault（uk_template_default 的键带 state，见 V55） */
    void delete(Integer templateId);
}
