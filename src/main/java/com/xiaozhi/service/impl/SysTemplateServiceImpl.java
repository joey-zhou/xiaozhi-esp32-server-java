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
    private SysTemplateRepository sysTemplateRepository;

    /**
     * 添加模板
     */
    @Override
    @Transactional
    public int add(SysTemplate template) {
        // 如果是默认模板，先重置其他默认模板
        if (template.getIsDefault() != null && template.getIsDefault().equals("1")) {
            sysTemplateRepository.resetDefault(template.getUserId());
        }
        sysTemplateRepository.save(template);
        return 1;
    }

    /**
     * 修改模板
     */
    @Override
    @Transactional
    public int update(SysTemplate template) {
        // 如果是默认模板，先重置其他默认模板
        if (template.getIsDefault() != null && template.getIsDefault().equals("1")) {
            sysTemplateRepository.resetDefault(template.getUserId());
        }
        sysTemplateRepository.save(template);
        return 1;
    }

    /**
     * 删除模板
     */
    @Override
    @Transactional
    public int delete(Integer templateId) {
        return sysTemplateRepository.deleteTemplateById(templateId);
    }

    /**
     * 查询模板列表
     */
    @Override
    public List<SysTemplate> query(SysTemplate template) {
        return sysTemplateRepository.findTemplates(
                template.getUserId(),
                template.getCategory(),
                template.getState()
        );
    }

    /**
     * 查询模板详情
     */
    @Override
    public SysTemplate selectTemplateById(Integer templateId) {
        return sysTemplateRepository.findTemplateById(templateId);
    }

}
