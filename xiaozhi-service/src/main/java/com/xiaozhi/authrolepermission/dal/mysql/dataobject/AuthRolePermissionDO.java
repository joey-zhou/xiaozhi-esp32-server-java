package com.xiaozhi.authrolepermission.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_auth_role_permission")
public class AuthRolePermissionDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer authRoleId;
    private Integer permissionId;
    private LocalDateTime createTime;
}
