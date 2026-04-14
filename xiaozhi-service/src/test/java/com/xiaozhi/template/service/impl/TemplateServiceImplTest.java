package com.xiaozhi.template.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaozhi.common.model.bo.TemplateBO;
import com.xiaozhi.support.MybatisPlusTestHelper;
import com.xiaozhi.template.convert.TemplateConvert;
import com.xiaozhi.template.dal.mysql.dataobject.TemplateDO;
import com.xiaozhi.template.dal.mysql.mapper.TemplateMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(TemplateDO.class);
    }

    @Mock
    private TemplateMapper templateMapper;

    @Mock
    private TemplateConvert templateConvert;

    @InjectMocks
    private TemplateServiceImpl templateService;

    @Test
    void listBOReturnsMappedTemplates() {
        TemplateDO templateDO = new TemplateDO();
        templateDO.setTemplateId(1);
        TemplateBO templateBO = new TemplateBO();
        templateBO.setTemplateId(1);

        when(templateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(templateDO));
        when(templateConvert.toBO(templateDO)).thenReturn(templateBO);

        List<TemplateBO> result = templateService.listBO(1, "默认", "chat");

        assertThat(result).containsExactly(templateBO);
        verify(templateMapper).selectList(any(LambdaQueryWrapper.class));
        verify(templateConvert).toBO(templateDO);
    }

}
