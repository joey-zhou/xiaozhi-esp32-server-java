package com.xiaozhi.verifycode.service;

import com.xiaozhi.common.model.bo.VerifyCodeBO;

/**
 * 验证码读写（sys_code 表），服务设备激活与邮箱注册两个场景。
 */
public interface VerifyCodeService {

    /** deviceId / sessionId / code 三个条件非空才参与过滤，取有效期内最新的一条，无则返回 null。 */
    VerifyCodeBO findValid(String code, String deviceId, String sessionId);

    int createForDevice(String deviceId, String sessionId, String type, String code);

    int deleteByDeviceId(String deviceId);

    int updateAudioPath(String deviceId, String sessionId, String code, String audioPath);

    int createForEmail(String email, String code);

    boolean hasValidEmail(String email, String code);
}
