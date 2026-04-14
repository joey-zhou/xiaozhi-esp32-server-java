package com.xiaozhi.common.model.bo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户第三方授权信息 BO（对应 sys_user_auth 表）。
 */
@Data
public class UserAuthBO {

    private Long id;
    private Integer userId;
    private String openId;
    private String unionId;
    private String platform;
    private String profile;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
