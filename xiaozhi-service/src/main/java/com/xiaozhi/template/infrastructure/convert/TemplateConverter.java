package com.xiaozhi.template.infrastructure.convert;

import com.xiaozhi.common.model.bo.TemplateBO;
import com.xiaozhi.template.dal.mysql.dataobject.TemplateDO;
import com.xiaozhi.template.domain.Template;
import org.springframework.stereotype.Component;

/**
 * TemplateDO / TemplateBO ↔ Template 聚合根转换器。
 */
@Component
public class TemplateConverter {

    public Template toDomain(TemplateDO d) {
        if (d == null) return null;
        return Template.reconstitute(
                d.getTemplateId(), d.getUserId(),
                d.getTemplateName(), d.getTemplateDesc(),
                d.getTemplateContent(), d.getCategory(),
                d.getState(), "1".equals(d.getIsDefault()),
                d.getCreateTime(), d.getUpdateTime());
    }

    public TemplateDO toDO(Template t) {
        TemplateDO d = new TemplateDO();
        d.setTemplateId(t.getTemplateId());
        d.setUserId(t.getUserId());
        d.setTemplateName(t.getTemplateName());
        d.setTemplateDesc(t.getTemplateDesc());
        d.setTemplateContent(t.getTemplateContent());
        d.setCategory(t.getCategory());
        d.setState(t.getState() != null ? t.getState() : Template.STATE_ENABLED);
        d.setIsDefault(t.isDefault() ? "1" : "0");
        return d;
    }

    public TemplateBO toBO(Template t) {
        if (t == null) return null;
        TemplateBO bo = new TemplateBO();
        bo.setTemplateId(t.getTemplateId());
        bo.setUserId(t.getUserId());
        bo.setTemplateName(t.getTemplateName());
        bo.setTemplateDesc(t.getTemplateDesc());
        bo.setTemplateContent(t.getTemplateContent());
        bo.setCategory(t.getCategory());
        bo.setState(t.getState());
        bo.setIsDefault(t.isDefault() ? "1" : "0");
        bo.setCreateTime(t.getCreateTime());
        bo.setUpdateTime(t.getUpdateTime());
        return bo;
    }
}
