package com.xiaozhi.verifycode.service;

import com.xiaozhi.common.model.bo.VerifyCodeBO;

/**
 * 验证码读写（sys_code 表），服务设备激活与邮箱注册两个场景。
 */
public interface VerifyCodeService {

    /**
     * deviceId / sessionId / code 三个条件非空才参与过滤，取有效期内最新的一条；三者全空或无匹配返回 null。
     * 只查设备码，不匹配邮箱码。
     */
    VerifyCodeBO findValid(String code, String deviceId, String sessionId);

    /**
     * 只凭 code 定位设备码（绑定场景）：有效期内命中多于一条时无法判定属于哪台设备，返回 null，由用户重新获取。
     */
    VerifyCodeBO findValidByCode(String code);

    int createForDevice(String deviceId, String sessionId, String type, String code);

    int deleteByDeviceId(String deviceId);

    int updateAudioPath(String deviceId, String sessionId, String code, String audioPath);

    /** 写入前先删掉该账号所有旧码，同一账号任一时刻只有一条有效码。 */
    int createForEmail(String email, String code);

    /**
     * 校验并消费账号验证码：命中即删除该行，删除影响行数为 0 视为失败。
     * 同一账号连续失败达上限后，剩余未过期的码全部作废，必须重新获取。
     */
    boolean consumeByAccount(String account, String code);
}
