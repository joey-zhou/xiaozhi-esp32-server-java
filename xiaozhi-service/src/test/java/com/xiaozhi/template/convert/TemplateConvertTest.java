package com.xiaozhi.template.convert;

import com.xiaozhi.common.model.bo.TemplateBO;
import com.xiaozhi.common.model.req.TemplateCreateReq;
import com.xiaozhi.template.dal.mysql.dataobject.TemplateDO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模板的部分更新与归属：updateDO 里为 null 的字段不许覆盖，
 * 创建请求也不许自带 templateId / userId——否则调用方能把别人的模板改到自己名下。
 */
class TemplateConvertTest {

    private final TemplateConvert convert = Mappers.getMapper(TemplateConvert.class);

    @Test
    void updateDoKeepsFieldsThatWereNotProvided() {
        TemplateDO existing = new TemplateDO();
        existing.setTemplateId(5);
        existing.setUserId(2);
        existing.setTemplateName("旧名字");
        existing.setTemplateContent("你是一个助手");

        TemplateBO patch = new TemplateBO();
        patch.setTemplateName("新名字");

        convert.updateDO(patch, existing);

        assertThat(existing.getTemplateName()).isEqualTo("新名字");
        assertThat(existing.getTemplateContent())
            .as("模板正文被清空，等于这个角色的人设直接没了")
            .isEqualTo("你是一个助手");
        assertThat(existing.getTemplateId()).isEqualTo(5);
        assertThat(existing.getUserId()).isEqualTo(2);
    }

    @Test
    void createRequestCannotChooseItsOwnIdOrOwner() {
        TemplateCreateReq req = new TemplateCreateReq();
        req.setTemplateName("默认助手");
        req.setTemplateContent("你是一个助手");

        TemplateBO bo = convert.toBO(req);

        assertThat(bo.getTemplateName()).isEqualTo("默认助手");
        assertThat(bo.getTemplateContent()).isEqualTo("你是一个助手");
        assertThat(bo.getTemplateId()).isNull();
        assertThat(bo.getUserId()).as("归属由登录态决定，请求带过来的一律不认").isNull();
    }

    @Test
    void boToDoDropsTheIdSoInsertsAlwaysGetAFreshOne() {
        TemplateBO bo = new TemplateBO();
        bo.setTemplateId(999);
        bo.setUserId(2);
        bo.setTemplateName("默认助手");

        TemplateDO d = convert.toDO(bo);

        assertThat(d.getTemplateId()).isNull();
        assertThat(d.getUserId()).isEqualTo(2);
        assertThat(d.getTemplateName()).isEqualTo("默认助手");
    }
}
