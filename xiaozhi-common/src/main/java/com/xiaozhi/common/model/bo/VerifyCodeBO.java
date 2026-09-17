package com.xiaozhi.common.model.bo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 验证码 BO（对应 sys_code 表）。
 * <p>
 * sys_code 表为多用途验证码表，不同场景使用不同字段：
 * <ul>
 *   <li>设备激活：deviceId、sessionId、type、code、audioPath</li>
 *   <li>账号验证（注册/登录/找回密码）：account、code</li>
 * </ul>
 */
@Data
public class VerifyCodeBO {

    /** 收码账号：邮箱或手机号，两种渠道共用一列；设备激活场景为 null */
    private String account;

    private String deviceId;

    private String sessionId;

    private String type;

    private String code;

    private String audioPath;

    private LocalDateTime createTime;
}
