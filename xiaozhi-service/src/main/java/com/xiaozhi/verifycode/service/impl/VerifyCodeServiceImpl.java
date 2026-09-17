package com.xiaozhi.verifycode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.verifycode.convert.VerifyCodeConvert;
import com.xiaozhi.verifycode.dal.mysql.dataobject.VerifyCodeDO;
import com.xiaozhi.verifycode.dal.mysql.mapper.VerifyCodeMapper;
import com.xiaozhi.verifycode.service.VerifyCodeService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class VerifyCodeServiceImpl implements VerifyCodeService {

    private static final int VALID_MINUTES = 10;

    /** 同一账号在有效期窗口内允许的校验次数，达到即作废该账号所有未过期的码。 */
    private static final int MAX_ATTEMPTS = 5;

    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(VALID_MINUTES);

    private static final String ATTEMPT_KEY_PREFIX = "xiaozhi:captcha:attempt:";

    @Resource
    private VerifyCodeMapper verifyCodeMapper;

    @Resource
    private VerifyCodeConvert verifyCodeConvert;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public VerifyCodeBO findValid(String code, String deviceId, String sessionId) {
        // 三个维度全空时查询会退化成「有效期内最新一条」，跨设备匹配，直接拒绝
        if (!StringUtils.hasLength(code) && !StringUtils.hasLength(deviceId) && !StringUtils.hasLength(sessionId)) {
            return null;
        }
        VerifyCodeDO verifyCode = verifyCodeMapper.selectOne(deviceCodeQuery(code, deviceId, sessionId)
            .orderByDesc(VerifyCodeDO::getCreateTime)
            .last("LIMIT 1"));
        return verifyCodeConvert.toBO(verifyCode);
    }

    @Override
    public VerifyCodeBO findValidByCode(String code) {
        if (!StringUtils.hasLength(code)) {
            return null;
        }
        List<VerifyCodeDO> matched = verifyCodeMapper.selectList(deviceCodeQuery(code, null, null)
            .last("LIMIT 2"));
        // 同一个码命中多条时无法判定属于哪台设备，一律拒绝，由用户重新获取
        if (matched.size() != 1) {
            return null;
        }
        return verifyCodeConvert.toBO(matched.get(0));
    }

    /** 设备码查询条件：非空维度才参与过滤；email 为空把邮箱码那半张表排除在外。 */
    private LambdaQueryWrapper<VerifyCodeDO> deviceCodeQuery(String code, String deviceId, String sessionId) {
        return new LambdaQueryWrapper<VerifyCodeDO>()
            .eq(StringUtils.hasLength(deviceId), VerifyCodeDO::getDeviceId, deviceId)
            .eq(StringUtils.hasLength(sessionId), VerifyCodeDO::getSessionId, sessionId)
            .eq(StringUtils.hasLength(code), VerifyCodeDO::getCode, code)
            .isNull(VerifyCodeDO::getEmail)
            .ge(VerifyCodeDO::getCreateTime, validSince());
    }

    @Override
    public int createForDevice(String deviceId, String sessionId, String type, String code) {
        VerifyCodeDO verifyCode = new VerifyCodeDO();
        verifyCode.setDeviceId(deviceId);
        verifyCode.setSessionId(sessionId);
        verifyCode.setType(type);
        verifyCode.setCode(code);
        verifyCode.setCreateTime(LocalDateTime.now());
        return verifyCodeMapper.insert(verifyCode);
    }

    @Override
    public int deleteByDeviceId(String deviceId) {
        return verifyCodeMapper.delete(new LambdaQueryWrapper<VerifyCodeDO>()
            .eq(VerifyCodeDO::getDeviceId, deviceId));
    }

    @Override
    public int updateAudioPath(String deviceId, String sessionId, String code, String audioPath) {
        return verifyCodeMapper.update(null, new LambdaUpdateWrapper<VerifyCodeDO>()
            .eq(VerifyCodeDO::getDeviceId, deviceId)
            .eq(VerifyCodeDO::getSessionId, sessionId)
            .eq(VerifyCodeDO::getCode, code)
            .set(VerifyCodeDO::getAudioPath, audioPath));
    }

    @Override
    public int createForEmail(String email, String code) {
        deleteByAccount(email);
        resetAttempts(email);
        VerifyCodeDO verifyCode = new VerifyCodeDO();
        verifyCode.setEmail(email);
        verifyCode.setCode(code);
        verifyCode.setCreateTime(LocalDateTime.now());
        return verifyCodeMapper.insert(verifyCode);
    }

    @Override
    public boolean consumeByAccount(String account, String code) {
        if (!StringUtils.hasText(account) || !StringUtils.hasText(code)) {
            return false;
        }

        String attemptKey = attemptKey(account);
        Long attempts = stringRedisTemplate.opsForValue().increment(attemptKey);
        if (attempts != null && attempts == 1L) {
            stringRedisTemplate.expire(attemptKey, ATTEMPT_WINDOW);
        }
        if (attempts == null || attempts > MAX_ATTEMPTS) {
            deleteByAccount(account);
            return false;
        }

        // delete 的影响行数天然是原子的，并发只有一个调用方能拿到 1，重放拿到 0
        int consumed = verifyCodeMapper.delete(new LambdaQueryWrapper<VerifyCodeDO>()
            .eq(VerifyCodeDO::getEmail, account)
            .eq(VerifyCodeDO::getCode, code)
            .ge(VerifyCodeDO::getCreateTime, validSince()));
        if (consumed > 0) {
            stringRedisTemplate.delete(attemptKey);
            return true;
        }

        if (attempts == MAX_ATTEMPTS) {
            deleteByAccount(account);
        }
        return false;
    }

    /** 清零该账号的校验次数，重新发码后必须调用，否则新码会被上一轮攒下的次数直接判死。 */
    private void resetAttempts(String account) {
        if (!StringUtils.hasText(account)) {
            return;
        }
        stringRedisTemplate.delete(attemptKey(account));
    }

    private String attemptKey(String account) {
        return ATTEMPT_KEY_PREFIX + account;
    }

    /** 删除该账号名下所有验证码，不看有效期。 */
    private int deleteByAccount(String account) {
        if (!StringUtils.hasText(account)) {
            return 0;
        }
        return verifyCodeMapper.delete(new LambdaQueryWrapper<VerifyCodeDO>()
            .eq(VerifyCodeDO::getEmail, account));
    }

    /** 验证码有效期起点。 */
    private LocalDateTime validSince() {
        return LocalDateTime.now().minusMinutes(VALID_MINUTES);
    }
}
