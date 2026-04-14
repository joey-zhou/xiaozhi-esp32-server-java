package com.xiaozhi.template;

import com.xiaozhi.common.model.req.TemplateCreateReq;
import com.xiaozhi.common.model.req.TemplatePageReq;
import com.xiaozhi.common.model.req.TemplateUpdateReq;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.model.resp.TemplateResp;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.template.convert.TemplateConvert;
import com.xiaozhi.template.domain.Template;
import com.xiaozhi.template.domain.repository.TemplateRepository;
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

    @Resource
    private TemplateRepository templateRepository;

    public PageResp<TemplateResp> page(TemplatePageReq req, Integer userId) {
        TemplatePageReq r = req == null ? new TemplatePageReq() : req;
        return templateService.page(r.getPageNo(), r.getPageSize(), r.getTemplateName(), r.getCategory(), userId);
    }

    public TemplateResp create(TemplateCreateReq req, Integer userId) {
        Template template = Template.newTemplate(userId, templateConvert.toBO(req));
        templateRepository.save(template);
        return templateConvert.toResp(templateService.getBO(template.getTemplateId()));
    }

    public TemplateResp update(Integer templateId, TemplateUpdateReq req) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("模板不存在或无权访问"));
        template.update(templateConvert.toBO(req));
        templateRepository.save(template);
        return templateConvert.toResp(templateService.getBO(templateId));
    }

    public void delete(Integer templateId) {
        templateRepository.delete(templateId);
    }
}
