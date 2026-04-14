package com.xiaozhi.template.convert;

import com.xiaozhi.common.model.bo.TemplateBO;
import com.xiaozhi.common.model.req.TemplateCreateReq;
import com.xiaozhi.common.model.req.TemplateUpdateReq;
import com.xiaozhi.common.model.resp.TemplateResp;
import com.xiaozhi.template.dal.mysql.dataobject.TemplateDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface TemplateConvert {

    @Mapping(target = "templateId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    TemplateDO toDO(TemplateCreateReq req);

    @Mapping(target = "templateId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDO(TemplateUpdateReq req, @MappingTarget TemplateDO templateDO);

    @Mapping(target = "templateId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    TemplateDO toDO(TemplateBO templateBO);

    TemplateBO toBO(TemplateDO templateDO);

    TemplateBO toBO(TemplateCreateReq req);

    TemplateBO toBO(TemplateUpdateReq req);

    TemplateResp toResp(TemplateDO templateDO);

    TemplateResp toResp(TemplateBO bo);

    @Mapping(target = "templateId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDO(TemplateBO bo, @MappingTarget TemplateDO templateDO);
}
