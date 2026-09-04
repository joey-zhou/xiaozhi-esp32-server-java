package com.xiaozhi.template;

import com.xiaozhi.common.model.req.TemplateCreateReq;
import com.xiaozhi.common.model.req.TemplatePageReq;
import com.xiaozhi.common.model.req.TemplateUpdateReq;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.model.resp.TemplateResp;
import com.xiaozhi.template.convert.TemplateConvert;
import com.xiaozhi.template.service.TemplateService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * Template 领域应用服务。
 * <p>
 * 职责：编排 Controller → Domain Service 之间的流程，包括：
 * <ul>
 *   <li>Req/Resp ↔ BO 转换</li>
 *   <li>模板管理编排</li>
 * </ul>
 */
@Service
public class TemplateAppService {

    @Resource
    private TemplateService templateService;

    @Resource
    private TemplateConvert templateConvert;

    public PageResp<TemplateResp> page(TemplatePageReq req, Integer userId) {
        TemplatePageReq r = req == null ? new TemplatePageReq() : req;
        return templateService.page(r.getPageNo(), r.getPageSize(), r.getTemplateName(), r.getCategory(), userId);
    }

    public TemplateResp create(TemplateCreateReq req, Integer userId) {
        return templateConvert.toResp(templateService.create(userId, templateConvert.toBO(req)));
    }

    public TemplateResp update(Integer templateId, TemplateUpdateReq req) {
        return templateConvert.toResp(templateService.update(templateId, templateConvert.toBO(req)));
    }

    public void delete(Integer templateId) {
        templateService.delete(templateId);
    }
}
