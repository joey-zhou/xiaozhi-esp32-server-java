package com.xiaozhi.user.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaozhi.common.model.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class UserDO extends BaseDO {

    @TableId(value = "userId", type = IdType.AUTO)
    private Integer userId;

    private String username;
    private String password;
    private String wxOpenId;
    private String wxUnionId;
    private String name;
    private String avatar;
    private String state;
    private String isAdmin;
    private Integer authRoleId;
    private String tel;
    private String email;
    private String loginIp;
    private LocalDateTime loginTime;
}
