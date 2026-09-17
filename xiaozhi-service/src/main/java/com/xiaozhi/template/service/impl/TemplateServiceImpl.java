package com.xiaozhi.template.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.TemplateBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.template.convert.TemplateConvert;
import com.xiaozhi.template.dal.mysql.dataobject.TemplateDO;
import com.xiaozhi.template.dal.mysql.mapper.TemplateMapper;
import com.xiaozhi.template.service.TemplateService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TemplateServiceImpl implements TemplateService {

    private static final String STATE_ENABLED = "1";
    private static final String STATE_DISABLED = "0";
    private static final String IS_DEFAULT = "1";
    private static final String NOT_DEFAULT = "0";

    @Resource
    private TemplateMapper templateMapper;

    @Resource
    private TemplateConvert templateConvert;

    @Override
    public PageResult<TemplateBO> page(int pageNo, int pageSize, String templateName, String category, Integer userId) {
        Page<TemplateDO> page = new Page<>(pageNo, pageSize);
        IPage<TemplateDO> result = templateMapper.selectPage(page, buildQuery(userId, templateName, category));
        List<TemplateBO> list = result.getRecords().stream()
            .map(templateConvert::toBO)
            .toList();
        return new PageResult<>(
            list,
            result.getTotal(),
            Math.toIntExact(result.getCurrent()),
            Math.toIntExact(result.getSize())
        );
    }


    @Override
    public TemplateBO getBO(Integer templateId) {
        return templateConvert.toBO(getTemplate(templateId));
    }

    @Override
    public List<TemplateBO> listBO(Integer userId, String templateName, String category) {
        return templateMapper.selectList(buildQuery(userId, templateName, category)).stream()
            .map(templateConvert::toBO)
            .toList();
    }

    private LambdaQueryWrapper<TemplateDO> buildQuery(Integer userId, String templateName, String category) {
        LambdaQueryWrapper<TemplateDO> queryWrapper = new LambdaQueryWrapper<TemplateDO>()
            .eq(TemplateDO::getUserId, userId)
            .eq(TemplateDO::getState, TemplateBO.STATE_ENABLED);
        if (StringUtils.hasText(templateName)) {
            queryWrapper.like(TemplateDO::getTemplateName, templateName);
        }
        if (StringUtils.hasText(category)) {
            queryWrapper.eq(TemplateDO::getCategory, category);
        }
        return queryWrapper.orderByDesc(TemplateDO::getIsDefault, TemplateDO::getCreateTime);
    }

    private TemplateDO getTemplate(Integer templateId) {
        if (templateId == null) {
            return null;
        }
        return templateMapper.selectOne(new LambdaQueryWrapper<TemplateDO>()
            .eq(TemplateDO::getTemplateId, templateId)
            .eq(TemplateDO::getState, TemplateBO.STATE_ENABLED));
    }


    @Override
    @Transactional
    public void copyTemplates(Integer sourceUserId, Integer targetUserId) {
        for (TemplateBO template : listBO(sourceUserId, null, null)) {
            create(targetUserId, template);
        }
    }

    @Override
    @Transactional
    public TemplateBO create(Integer userId, TemplateBO bo) {
        TemplateDO d = templateConvert.toDO(bo);
        d.setUserId(userId);
        d.setState(STATE_ENABLED);
        if (d.getIsDefault() == null) {
            d.setIsDefault(NOT_DEFAULT);
        }
        if (IS_DEFAULT.equals(d.getIsDefault())) {
            resetDefault(userId, null);
        }
        templateMapper.insert(d);
        return templateConvert.toBO(d);
    }

    @Override
    @Transactional
    public TemplateBO update(Integer templateId, TemplateBO bo) {
        TemplateDO d = getTemplate(templateId);
        if (d == null) {
            throw new ResourceNotFoundException("模板不存在或已删除");
        }
        boolean turningDefault = IS_DEFAULT.equals(bo.getIsDefault()) && !IS_DEFAULT.equals(d.getIsDefault());
        templateConvert.updateDO(bo, d);
        if (turningDefault) {
            resetDefault(d.getUserId(), templateId);
        }
        templateMapper.updateById(d);
        return templateConvert.toBO(d);
    }

    @Override
    @Transactional
    public void delete(Integer templateId) {
        templateMapper.update(null, new LambdaUpdateWrapper<TemplateDO>()
            .eq(TemplateDO::getTemplateId, templateId)
            .eq(TemplateDO::getState, STATE_ENABLED)
            .set(TemplateDO::getState, STATE_DISABLED));
    }

    private void resetDefault(Integer userId, Integer excludeTemplateId) {
        LambdaUpdateWrapper<TemplateDO> w = new LambdaUpdateWrapper<TemplateDO>()
            .eq(TemplateDO::getUserId, userId)
            .eq(TemplateDO::getState, STATE_ENABLED)
            .eq(TemplateDO::getIsDefault, IS_DEFAULT)
            .set(TemplateDO::getIsDefault, NOT_DEFAULT);
        if (excludeTemplateId != null) {
            w.ne(TemplateDO::getTemplateId, excludeTemplateId);
        }
        templateMapper.update(null, w);
    }
}
