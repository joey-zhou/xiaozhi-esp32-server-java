package com.xiaozhi.verifycode.service;

import com.xiaozhi.common.model.bo.VerifyCodeBO;

/**
 * 验证码读写（sys_code 表），服务设备激活与账号（邮箱/手机号）两个场景。
 */
public interface VerifyCodeService {

    /**
     * deviceId / sessionId / code 三个条件非空才参与过滤，取有效期内最新的一条；三者全空或无匹配返回 null。
     * 只查设备码，不匹配账号码。
     */
    VerifyCodeBO findValid(String code, String deviceId, String sessionId);

    /**
     * 只凭 code 定位设备码（绑定场景）：有效期内命中多于一条时无法判定属于哪台设备，返回 null，由用户重新获取。
     */
    VerifyCodeBO findValidByCode(String code);

    int createForDevice(String deviceId, String sessionId, String type, String code);

    int deleteByDeviceId(String deviceId);

    int updateAudioPath(String deviceId, String sessionId, String code, String audioPath);

    /**
     * 给账号（邮箱或手机号）发一条新的 6 位验证码并落库，返回明文由调用方投递。
     * <p>
     * 写入前先删掉该账号所有旧码并清零失败计数，同一账号任一时刻只有一条有效码。
     *
     * @throws IllegalArgumentException 账号为空
     * @throws IllegalStateException    落库失败
     */
    String generateForAccount(String account);

    /**
     * 校验并消费账号验证码：命中即删除该行，删除影响行数为 0 视为失败。
     * 同一账号连续失败达上限后，剩余未过期的码全部作废，必须重新获取。
     */
    boolean consumeByAccount(String account, String code);

    /**
     * 清理早已过期的验证码行：设备码/账号码一旦过了有效期就不再被任何查询命中，
     * 只增不删会让 sys_code 无限堆积，定时任务按创建时间批量删除。
     *
     * @return 本次删除的行数
     */
    int deleteExpired(int expiredBeforeMinutes, int batchSize);
}
