package com.xiaozhi.service.impl;

import com.xiaozhi.entity.SysTemplate;
import com.xiaozhi.repository.SysTemplateRepository;
import com.xiaozhi.service.SysTemplateService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 提示词模板服务实现类
 */
@Service
public class SysTemplateServiceImpl implements SysTemplateService {

    @Resource
    private SysTemplateRepository templateRepository;

    @Override
    @Transactional
    public int add(SysTemplate template) {
        if (template.getIsDefault() != null && template.getIsDefault().equals("1")) {
            templateRepository.resetDefault(template);
        }
        return templateRepository.add(template);
    }

    @Override
    @Transactional
    public int update(SysTemplate template) {
        if (template.getIsDefault() != null && template.getIsDefault().equals("1")) {
            templateRepository.resetDefault(template);
        }
        return templateRepository.update(template);
    }

    @Override
    public int delete(Integer templateId) {
        return templateRepository.delete(templateId);
    }

    @Override
    public List<SysTemplate> query(SysTemplate template) {
        return templateRepository.query(template);
    }

    @Override
    public SysTemplate selectTemplateById(Integer templateId) {
        return templateRepository.selectTemplateById(templateId);
    }
}
