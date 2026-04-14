package com.xiaozhi.authrole.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaozhi.common.model.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_role")
public class AuthRoleDO extends BaseDO {

    @TableId(value = "authRoleId", type = IdType.AUTO)
    private Integer authRoleId;

    private String authRoleName;
    private String roleKey;
    private String description;
    private String status;
}
