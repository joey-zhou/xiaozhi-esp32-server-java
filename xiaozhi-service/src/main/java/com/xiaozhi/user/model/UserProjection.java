package com.xiaozhi.user.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户分页结果集，字段名与 UserMapper.xml 的列别名逐字一致；tel 已在 SQL 里脱敏。 */
@Data
public class UserProjection {

    private Integer userId;
    private String username;
    private String name;
    private String tel;
    private String email;
    private String avatar;
    private String state;
    private String isAdmin;
    private Integer authRoleId;
    private String authRoleName;
    private String loginIp;
    private LocalDateTime loginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer totalDevice;
    private Integer totalMessage;
    private Integer aliveNumber;
}
