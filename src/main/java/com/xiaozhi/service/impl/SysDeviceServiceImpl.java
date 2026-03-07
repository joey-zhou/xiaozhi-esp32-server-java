package com.xiaozhi.service.impl;

import com.xiaozhi.common.cache.BloomFilterManager;
import com.xiaozhi.common.exception.NotFoundException;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.repository.SysDeviceRepository;
import com.xiaozhi.repository.SysMessageRepository;
import com.xiaozhi.repository.SysRoleRepository;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysDeviceService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 设备操作
 *
 * @author Joey
 */
@Service
public class SysDeviceServiceImpl extends BaseServiceImpl implements SysDeviceService {
    private static final Logger logger = LoggerFactory.getLogger(SysDeviceServiceImpl.class);
    private final static String CACHE_NAME = "XiaoZhi:SysDevice";

    @Autowired
    private SysDeviceRepository sysDeviceRepository;

    @Autowired
    private SysMessageRepository sysMessageRepository;

    @Autowired
    private SysRoleRepository sysRoleRepository;

    @Resource
    private SysConfigService configService;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private BloomFilterManager bloomFilterManager;

    @Override
    @Transactional
    public int add(SysDevice device) throws NotFoundException {
        SysDevice existingDevice = sysDeviceRepository.selectDeviceById(device.getDeviceId());
        if (existingDevice != null) {
            return 1;
        }

        SysRole queryRole = new SysRole();
        queryRole.setUserId(device.getUserId());
        List<SysRole> roles = sysRoleRepository.query(queryRole);

        if (roles.isEmpty()) {
            throw new NotFoundException("没有配置角色");
        }

        SysRole selectedRole = null;
        for (SysRole role : roles) {
            if (("1").equals(role.getIsDefault())) {
                selectedRole = role;
                break;
            }
        }
        if (selectedRole == null) {
            selectedRole = roles.getFirst();
        }

        device.setRoleId(selectedRole.getRoleId());
        sysDeviceRepository.save(device);

        if (device.getDeviceId() != null) {
            bloomFilterManager.addDeviceId(device.getDeviceId());
        }

        return 1;
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "#device.deviceId?.replace(\":\", \"-\")", condition = "#device.deviceId != null")
    public int delete(SysDevice device) {
        int row = sysDeviceRepository.deleteDevice(device);
        if (row > 0) {
            sysMessageRepository.deleteByDeviceAndUser(device.getDeviceId(), device.getUserId());
        }
        return row;
    }

    @Override
    public List<SysDevice> query(SysDevice device, PageFilter pageFilter) {
        if (pageFilter != null) {
            return sysDeviceRepository.findDevices(
                    device.getUserId(),
                    device.getDeviceName(),
                    device.getState(),
                    org.springframework.data.domain.PageRequest.of(pageFilter.getStart() - 1, pageFilter.getLimit())
            ).getContent();
        }
        return sysDeviceRepository.query(device);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#deviceId.replace(\":\", \"-\")", unless = "#result == null")
    public SysDevice selectDeviceById(String deviceId) {
        if (!bloomFilterManager.mightContain(deviceId)) {
            logger.debug("布隆过滤器拦截：设备 ID 不存在 - {}", deviceId);
            return null;
        }
        return sysDeviceRepository.selectDeviceById(deviceId);
    }

    @Override
    public SysDevice queryVerifyCode(SysDevice device) {
        return sysDeviceRepository.queryVerifyCode(device);
    }

    @Override
    public SysDevice generateCode(SysDevice device) {
        SysDevice result = sysDeviceRepository.queryVerifyCode(device);
        if (result == null) {
            result = new SysDevice();
            sysDeviceRepository.generateCode(device);
            result.setCode(device.getCode());
        }
        return result;
    }

    @Override
    public int updateCode(SysDevice device) {
        return sysDeviceRepository.updateCode(device);
    }

    @Override
    public int batchUpdate(List<String> deviceIds, Integer userId, Integer roleId) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return 0;
        }
        return sysDeviceRepository.batchUpdateDevices(deviceIds, userId, roleId);
    }

    @Override
    @CacheEvict(value = CACHE_NAME, key = "#device.deviceId?.replace(\":\", \"-\")")
    public int update(SysDevice device) {
        if (device.getDeviceId() == null || device.getDeviceId().isEmpty()) {
            return updateAllDevices(device);
        }

        String deviceId = device.getDeviceId();
        SysDevice existingDevice = sysDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("设备不存在：" + deviceId));

        existingDevice.setDeviceName(device.getDeviceName())
                .setRoleId(device.getRoleId())
                .setFunctionNames(device.getFunctionNames())
                .setIp(device.getIp())
                .setLocation(device.getLocation())
                .setWifiName(device.getWifiName())
                .setChipModelName(device.getChipModelName())
                .setType(device.getType())
                .setVersion(device.getVersion())
                .setState(device.getState())
                .setUserId(device.getUserId());
        sysDeviceRepository.save(existingDevice);

        device = existingDevice;
        ChatSession session = null;
        if (device != null) {
            SessionManager sessionManager = applicationContext.getBean(SessionManager.class);
            session = sessionManager.getSessionByDeviceId(device.getDeviceId());
        }
        if (session != null) {
            session.setSysDevice(device);
        }
        return 1;
    }

    @Transactional
    public int updateAllDevices(SysDevice device) {
        return sysDeviceRepository.updateAllStates(device.getState());
    }
}
