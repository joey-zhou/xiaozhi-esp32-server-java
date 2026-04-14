package com.xiaozhi.template.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaozhi.common.model.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_template")
public class TemplateDO extends BaseDO {

    @TableId(value = "templateId", type = IdType.AUTO)
    private Integer templateId;

    private Integer userId;
    private String templateName;
    private String templateDesc;
    private String templateContent;
    private String category;
    private String isDefault;
    private String state;
}
