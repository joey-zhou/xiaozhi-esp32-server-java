package com.xiaozhi.common.model.bo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserBO {

    public static final String STATE_ENABLED = "1";
    public static final String STATE_DISABLED = "0";
    public static final String ADMIN_NO = "0";
    public static final String ADMIN_YES = "1";

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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
