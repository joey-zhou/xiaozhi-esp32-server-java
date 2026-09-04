package com.xiaozhi.verifycode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.verifycode.convert.VerifyCodeConvert;
import com.xiaozhi.verifycode.dal.mysql.dataobject.VerifyCodeDO;
import com.xiaozhi.verifycode.dal.mysql.mapper.VerifyCodeMapper;
import com.xiaozhi.verifycode.service.VerifyCodeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class VerifyCodeServiceImpl implements VerifyCodeService {

    private static final int VALID_MINUTES = 10;

    @Resource
    private VerifyCodeMapper verifyCodeMapper;

    @Resource
    private VerifyCodeConvert verifyCodeConvert;

    @Override
    public VerifyCodeBO findValid(String code, String deviceId, String sessionId) {
        VerifyCodeDO verifyCode = verifyCodeMapper.selectOne(new LambdaQueryWrapper<VerifyCodeDO>()
            .eq(StringUtils.hasLength(deviceId), VerifyCodeDO::getDeviceId, deviceId)
            .eq(StringUtils.hasLength(sessionId), VerifyCodeDO::getSessionId, sessionId)
            .eq(StringUtils.hasLength(code), VerifyCodeDO::getCode, code)
            .ge(VerifyCodeDO::getCreateTime, validSince())
            .orderByDesc(VerifyCodeDO::getCreateTime)
            .last("LIMIT 1"));
        return verifyCodeConvert.toBO(verifyCode);
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
        VerifyCodeDO verifyCode = new VerifyCodeDO();
        verifyCode.setEmail(email);
        verifyCode.setCode(code);
        verifyCode.setCreateTime(LocalDateTime.now());
        return verifyCodeMapper.insert(verifyCode);
    }

    @Override
    public boolean hasValidEmail(String email, String code) {
        return verifyCodeMapper.exists(new LambdaQueryWrapper<VerifyCodeDO>()
            .eq(VerifyCodeDO::getCode, code)
            .eq(VerifyCodeDO::getEmail, email)
            .ge(VerifyCodeDO::getCreateTime, validSince()));
    }

    /** 验证码有效期起点。 */
    private LocalDateTime validSince() {
        return LocalDateTime.now().minusMinutes(VALID_MINUTES);
    }
}
