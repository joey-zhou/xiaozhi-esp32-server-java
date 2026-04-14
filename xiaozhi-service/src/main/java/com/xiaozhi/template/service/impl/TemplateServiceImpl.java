package com.xiaozhi.template.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.model.bo.TemplateBO;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.model.resp.TemplateResp;
import com.xiaozhi.template.convert.TemplateConvert;
import com.xiaozhi.template.dal.mysql.dataobject.TemplateDO;
import com.xiaozhi.template.dal.mysql.mapper.TemplateMapper;
import com.xiaozhi.template.service.TemplateService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TemplateServiceImpl implements TemplateService {

    @Resource
    private TemplateMapper templateMapper;

    @Resource
    private TemplateConvert templateConvert;

    @Override
    public PageResp<TemplateResp> page(int pageNo, int pageSize, String templateName, String category, Integer userId) {
        Page<TemplateDO> page = new Page<>(pageNo, pageSize);
        IPage<TemplateDO> result = templateMapper.selectPage(page, buildQuery(userId, templateName, category));
        List<TemplateResp> list = result.getRecords().stream()
            .map(templateConvert::toResp)
            .toList();
        return new PageResp<>(
            list,
            result.getTotal(),
            Math.toIntExact(result.getCurrent()),
            Math.toIntExact(result.getSize())
        );
    }

    @Override
    public TemplateResp get(Integer templateId) {
        return templateConvert.toResp(getTemplate(templateId));
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

}
