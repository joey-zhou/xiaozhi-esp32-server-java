package com.xiaozhi.device.domain.vo;

import java.time.LocalDateTime;

/**
 * 验证码值对象（对应 sys_code 表的设备激活场景）。
 * <p>
 * 不可变，等值语义由 record 自动保证。
 */
public record VerifyCode(
        String code,
        String deviceId,
        String sessionId,
        String type,
        String audioPath,
        LocalDateTime createTime
) {}
