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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住模板列表的查询条件：始终限定 userId 与启用状态，
 * 模板名走模糊匹配、分类走等值匹配，默认模板排在前面。
 */
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
    void listBOFiltersByUserStateNameAndCategory() {
        TemplateDO templateDO = new TemplateDO();
        templateDO.setTemplateId(1);
        TemplateBO templateBO = new TemplateBO();
        templateBO.setTemplateId(1);

        when(templateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(templateDO));
        when(templateConvert.toBO(templateDO)).thenReturn(templateBO);

        List<TemplateBO> result = templateService.listBO(1, "默认", "chat");

        assertThat(result).containsExactly(templateBO);

        ArgumentCaptor<LambdaQueryWrapper<TemplateDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(templateMapper).selectList(captor.capture());
        assertThat(captor.getValue().getTargetSql())
            .contains("userId =")
            .contains("state =")
            .contains("templateName LIKE")
            .contains("category =")
            .containsSubsequence("ORDER BY", "isDefault", "createTime");
        assertThat(captor.getValue().getParamNameValuePairs().values())
            .containsExactlyInAnyOrder(1, TemplateBO.STATE_ENABLED, "%默认%", "chat");
    }

    @Test
    void listBOOmitsNameAndCategoryConditionsWhenBlank() {
        when(templateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThat(templateService.listBO(1, " ", null)).isEmpty();

        ArgumentCaptor<LambdaQueryWrapper<TemplateDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(templateMapper).selectList(captor.capture());
        assertThat(captor.getValue().getTargetSql())
            .doesNotContain("templateName")
            .doesNotContain("category");
        assertThat(captor.getValue().getParamNameValuePairs().values())
            .containsExactlyInAnyOrder(1, TemplateBO.STATE_ENABLED);
    }

}
