package com.xiaozhi.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.CacheHelper;
import com.xiaozhi.common.config.CacheNames;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.device.convert.DeviceConvert;
import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import com.xiaozhi.device.dal.mysql.mapper.DeviceMapper;
import com.xiaozhi.device.domain.Device;
import com.xiaozhi.device.domain.repository.DeviceRepository;
import com.xiaozhi.device.model.DeviceProjection;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.device.support.DeviceCacheKeys;
import com.xiaozhi.verifycode.service.VerifyCodeService;
import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DeviceServiceImpl implements DeviceService {

    /** 验证码摇号最多重试次数 */
    private static final int CODE_ALLOCATE_ATTEMPTS = 5;

    @Resource
    private DeviceMapper deviceMapper;

    @Resource
    private DeviceRepository deviceRepository;

    @Resource
    private VerifyCodeService verifyCodeService;

    @Resource
    private DeviceConvert deviceConvert;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private CacheHelper cacheHelper;

    @Override
    public PageResult<DeviceProjection> page(int pageNo, int pageSize, String deviceId, String deviceName,
                                           String roleName, String state, Integer roleId, Integer userId) {
        Page<DeviceProjection> page = new Page<>(pageNo, pageSize);
        IPage<DeviceProjection> result = deviceMapper.selectPage(page, deviceId, deviceName, roleName, state, roleId, userId);
        return new PageResult<>(
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
        String cacheKey = DeviceCacheKeys.of(deviceId);
        Cache cache = cacheManager.getCache(CacheNames.DEVICE);
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
    public List<DeviceBO> listByDeviceIds(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return List.of();
        }
        return deviceMapper.selectList(new LambdaQueryWrapper<DeviceDO>()
                .in(DeviceDO::getDeviceId, deviceIds))
            .stream()
            .map(deviceConvert::toBO)
            .toList();
    }

    @Override
    public List<DeviceBO> listByUserId(Integer userId) {
        if (userId == null) {
            return List.of();
        }
        return deviceMapper.selectList(new LambdaQueryWrapper<DeviceDO>()
                .eq(DeviceDO::getUserId, userId))
            .stream()
            .map(deviceConvert::toBO)
            .toList();
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
    @Transactional
    public VerifyCodeBO generateCode(String deviceId, String sessionId, String type) {
        VerifyCodeBO existingCode = verifyCodeService.findValid(null, deviceId, sessionId);
        if (existingCode != null) {
            return existingCode;
        }

        String code = allocateUnusedCode();
        verifyCodeService.createForDevice(deviceId, sessionId, type, code);
        return verifyCodeService.findValid(code, deviceId, sessionId);
    }

    /**
     * 摇一个有效期内没有被别的设备占用的 6 位码。
     * 绑定时只凭 6 位码定位设备，同一时刻存在两条相同的码就会绑错设备，因此必须摇号后探测占用。
     * 连续 CODE_ALLOCATE_ATTEMPTS 次都撞上说明码空间已被灌满，宁可拒绝发码也不能发出重复码。
     */
    private String allocateUnusedCode() {
        for (int i = 0; i < CODE_ALLOCATE_ATTEMPTS; i++) {
            String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            if (verifyCodeService.findValid(code, null, null) == null) {
                return code;
            }
        }
        throw new OperationFailedException("验证码分配失败，请稍后重试");
    }

    @Override
    public int updateCodeAudioPath(String deviceId, String sessionId, String code, String audioPath) {
        if (!StringUtils.hasText(deviceId) || !StringUtils.hasText(sessionId)
            || !StringUtils.hasText(code) || !StringUtils.hasText(audioPath)) {
            return 0;
        }
        return verifyCodeService.updateAudioPath(deviceId, sessionId, code, audioPath);
    }

    @Override
    public void updateState(String deviceId, String state) {
        deviceRepository.updateState(deviceId, state);
    }

    @Override
    public int batchUpdateState(Set<String> deviceIds, String state) {
        return deviceRepository.batchUpdateState(deviceIds, state);
    }

    @Override
    public void updateMcpList(String deviceId, String mcpList) {
        deviceRepository.findById(deviceId).ifPresent(device -> {
            device.updateMcpList(mcpList);
            deviceRepository.save(device);
        });
    }

    @Override
    public void bindRole(String deviceId, Integer roleId) {
        deviceRepository.findById(deviceId).ifPresent(device -> {
            device.bindRole(roleId);
            deviceRepository.save(device);
        });
    }

    @Override
    public void register(String deviceId, String deviceName, String type, Integer userId, Integer roleId) {
        deviceRepository.save(Device.newDevice(deviceId, deviceName, type, userId, roleId));
    }

}
