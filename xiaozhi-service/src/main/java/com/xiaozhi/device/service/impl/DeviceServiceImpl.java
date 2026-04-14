package com.xiaozhi.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.CacheHelper;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.common.model.resp.DeviceResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.device.convert.DeviceConvert;
import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import com.xiaozhi.device.dal.mysql.mapper.DeviceMapper;
import com.xiaozhi.device.service.DeviceService;
import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DeviceServiceImpl implements DeviceService {

    @Resource
    private DeviceMapper deviceMapper;

    @Resource
    private DeviceConvert deviceConvert;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private CacheHelper cacheHelper;

    @Override
    public PageResp<DeviceResp> page(int pageNo, int pageSize, String deviceId, String deviceName,
                                     String roleName, String state, Integer roleId, Integer userId) {
        Page<DeviceResp> page = new Page<>(pageNo, pageSize);
        IPage<DeviceResp> result = deviceMapper.selectPageResp(page, deviceId, deviceName, roleName, state, roleId, userId);
        return new PageResp<>(
            result.getRecords(),
            result.getTotal(),
            Math.toIntExact(result.getCurrent()),
            Math.toIntExact(result.getSize())
        );
    }

    @Override
    public DeviceBO getBO(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        String cacheKey = deviceId.replace(":", "-");
        org.springframework.cache.Cache cache = cacheManager.getCache(CACHE_NAME);
        return cacheHelper.getWithLock(
            "device:" + cacheKey,
            () -> cache == null ? null : cache.get(cacheKey, DeviceBO.class),
            () -> {
                DeviceBO result = deviceConvert.toBO(deviceMapper.selectOne(
                    new LambdaQueryWrapper<DeviceDO>().eq(DeviceDO::getDeviceId, deviceId)));
                if (result != null && cache != null) {
                    cache.put(cacheKey, result);
                }
                return result;
            }
        );
    }

    @Override
    public List<DeviceBO> listByStateAndType(String state, String type) {
        LambdaQueryWrapper<DeviceDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(state)) {
            queryWrapper.eq(DeviceDO::getState, state);
        }
        if (StringUtils.hasText(type)) {
            queryWrapper.eq(DeviceDO::getType, type);
        }
        return deviceMapper.selectList(queryWrapper).stream()
            .map(deviceConvert::toBO)
            .toList();
    }

    @Override
    public DeviceResp get(String deviceId) {
        return deviceMapper.selectRespById(deviceId, null);
    }

    private VerifyCodeBO queryVerifyCode(String code, String deviceId, String sessionId) {
        return deviceMapper.selectValidCode(code, deviceId, sessionId);
    }

    @Override
    @Transactional
    public VerifyCodeBO generateCode(String deviceId, String sessionId, String type) {
        VerifyCodeBO existingCode = queryVerifyCode(null, deviceId, sessionId);
        if (existingCode != null) {
            return existingCode;
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        deviceMapper.insertVerifyCode(deviceId, sessionId, type, code);
        return queryVerifyCode(code, deviceId, sessionId);
    }

    @Override
    public int updateCodeAudioPath(String deviceId, String sessionId, String code, String audioPath) {
        if (!StringUtils.hasText(deviceId) || !StringUtils.hasText(sessionId)
            || !StringUtils.hasText(code) || !StringUtils.hasText(audioPath)) {
            return 0;
        }
        return deviceMapper.updateCodeAudioPath(deviceId, sessionId, code, audioPath);
    }

}
