package com.xiaozhi.template.infrastructure;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaozhi.template.dal.mysql.dataobject.TemplateDO;
import com.xiaozhi.template.dal.mysql.mapper.TemplateMapper;
import com.xiaozhi.template.domain.Template;
import com.xiaozhi.template.domain.repository.TemplateRepository;
import com.xiaozhi.template.infrastructure.convert.TemplateConverter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class TemplateRepositoryImpl implements TemplateRepository {

    @Resource
    private TemplateMapper templateMapper;

    @Resource
    private TemplateConverter templateConverter;

    @Override
    public Optional<Template> findById(Integer templateId) {
        if (templateId == null) return Optional.empty();
        return Optional.ofNullable(templateConverter.toDomain(templateMapper.selectById(templateId)));
    }

    @Override
    @Transactional
    public void save(Template template) {
        boolean hasDefaultChanged = template.pullSignals()
                .contains(Template.DomainSignal.DEFAULT_CHANGED);

        if (hasDefaultChanged) {
            resetDefault(template.getUserId(), template.getTemplateId());
        }

        TemplateDO d = templateConverter.toDO(template);
        if (template.getTemplateId() == null) {
            templateMapper.insert(d);
            template.assignId(d.getTemplateId());
        } else {
            templateMapper.updateById(d);
        }
    }

    @Override
    @Transactional
    public void delete(Integer templateId) {
        templateMapper.update(null, new LambdaUpdateWrapper<TemplateDO>()
                .eq(TemplateDO::getTemplateId, templateId)
                .eq(TemplateDO::getState, Template.STATE_ENABLED)
                .set(TemplateDO::getState, Template.STATE_DISABLED));
    }

    private void resetDefault(Integer userId, Integer excludeTemplateId) {
        LambdaUpdateWrapper<TemplateDO> w = new LambdaUpdateWrapper<TemplateDO>()
                .eq(TemplateDO::getUserId, userId)
                .eq(TemplateDO::getState, Template.STATE_ENABLED)
                .eq(TemplateDO::getIsDefault, "1")
                .set(TemplateDO::getIsDefault, "0");
        if (excludeTemplateId != null) {
            w.ne(TemplateDO::getTemplateId, excludeTemplateId);
        }
        templateMapper.update(null, w);
    }
}
