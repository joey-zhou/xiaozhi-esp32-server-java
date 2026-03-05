package com.xiaozhi.service.impl;

import com.xiaozhi.common.cache.BloomFilterManager;
import com.xiaozhi.common.exception.NotFoundException;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.repository.SysDeviceRepository;
import com.xiaozhi.repository.SysMessageRepository;
import com.xiaozhi.repository.SysRoleRepository;
import com.xiaozhi.service.SysDeviceService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 设备操作
 *
 * @author Joey
 *
 */

@Service
public class SysDeviceServiceImpl extends BaseServiceImpl implements SysDeviceService {
    private static final Logger logger = LoggerFactory.getLogger(SysDeviceServiceImpl.class);

    private final static String CACHE_NAME = "XiaoZhi:SysDevice";

    @Resource
    private SysDeviceRepository sysDeviceRepository;

    @Resource
    private SysMessageRepository messageRepository;

    @Resource
    private SysRoleRepository roleRepository;

    @Resource
    private ApplicationContext applicationContext;


    @Resource
    private BloomFilterManager bloomFilterManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 添加设备
     *
     * @param device
     * @return
     * @throws NotFoundException 如果没有配置角色
     */
    @Override
    @Transactional
    public int add(SysDevice device) throws NotFoundException {

        SysDevice existingDevice = sysDeviceRepository.findById(device.getDeviceId()).orElse(null);
        if (existingDevice != null) {
            return 1;
        }

        // 查询是否有默认角色
        List<SysRole> roles = roleRepository.findAll();
        // 过滤出该用户的角色
        roles = roles.stream()
                .filter(r -> device.getUserId().equals(r.getUserId()))
                .toList();

        if (roles.isEmpty()) {
            throw new NotFoundException("没有配置角色");
        }

        SysRole selectedRole = null;

        // 优先绑定默认角色
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

        // 添加成功后，将设备 ID 加入布隆过滤器
        if (device.getDeviceId() != null) {
            bloomFilterManager.addDeviceId(device.getDeviceId());
        }

        return 1;

    }

    /**
     * 删除设备
     *
     * @param device
     * @return
     */
    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "#device.deviceId.replace(\":\", \"-\")")
    public int delete(SysDevice device) {
        int row = sysDeviceRepository.deleteDevice(device.getDeviceId());
        if (row > 0) {
            SysMessage message = new SysMessage();
            message.setUserId(device.getUserId());
            message.setDeviceId(device.getDeviceId());
            // 清空设备聊天记录
            messageRepository.deleteByDeviceAndUser(device.getDeviceId(), device.getUserId());
        }
        return row;
    }

    /**
     * 查询设备信息
     *
     * @param device
     * @return
     */
    @Override
    public List<SysDevice> query(SysDevice device, PageFilter pageFilter) {
        if (pageFilter != null) {
            Page<SysDevice> page = sysDeviceRepository.findDevices(
                    device.getUserId(),
                    device.getDeviceName(),
                    device.getState(),
                    PageRequest.of(pageFilter.getStart() - 1, pageFilter.getLimit(), Sort.by(Sort.Direction.DESC, "createTime"))
            );
            return page.getContent();
        }
        return sysDeviceRepository.findDevices(
                device.getUserId(),
                device.getDeviceName(),
                device.getState(),
                PageRequest.of(0, 10)
        ).getContent();
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#deviceId.replace(\":\", \"-\")", unless = "#result == null")
    public SysDevice selectDeviceById(String deviceId) {
        // 使用布隆过滤器快速判断设备是否不存在
        if (!bloomFilterManager.mightContain(deviceId)) {
            logger.debug("布隆过滤器拦截：设备 ID 不存在 - {}", deviceId);
            return null;  // 布隆过滤器判断一定不存在，直接返回 null
        }

        // 布隆过滤器判断可能存在，继续查询数据库
        return sysDeviceRepository.findDeviceById(deviceId).orElse(null);
    }

    /**
     * 查询验证码
     */
    @Override
    public SysDevice queryVerifyCode(SysDevice device) {
        return sysDeviceRepository.findVerifyCode(device.getDeviceId(), device.getUserId());
    }

    /**
     * 查询并生成验证码
     *
     */
    @Override
    public SysDevice generateCode(SysDevice device) {
        SysDevice result = sysDeviceRepository.findVerifyCode(device.getDeviceId(), device.getUserId());
        if (result == null) {
            result = new SysDevice();
            // TODO: 验证码生成逻辑需要单独处理
        }
        return result;
    }

    /**
     * 关系设备验证码语音路径
     */
    @Override
    @Transactional
    public int updateCode(SysDevice device) {
        return sysDeviceRepository.updateCode(device.getDeviceId(), device.getCode());
    }

    @Override
    @Transactional
    public int batchUpdate(List<String> deviceIds, Integer userId, Integer roleId) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return 0;
        }

        return sysDeviceRepository.batchUpdateDevices(deviceIds, userId, roleId);
    }

    /**
     * 更新设备信息
     *
     * @param device
     * @return
     */
    @Override
    @Transactional
     @CacheEvict(value = CACHE_NAME, key = "#device.deviceId.replace(\":\", \"-\")")
    public int update(SysDevice device) {
        // 如果 deviceId 为空，批量更新所有设备（用于项目启动时重置设备状态）
        if (device.getDeviceId() == null || device.getDeviceId().isEmpty()) {
            return updateAllDevices(device);
        }
        
        logger.debug("当前事务状态：active={}", TransactionSynchronizationManager.isActualTransactionActive() + "");
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        for (TransactionSynchronization synchronization : synchronizations) {
            logger.debug("事务同步：{}", synchronization.getClass().getName());
        }
        // 先查询已存在的实体，再更新字段，配合 @DynamicUpdate 只更新变化的字段
        String deviceId = device.getDeviceId();
        SysDevice existingDevice = sysDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("设备不存在：" + deviceId));
        // 拷贝需要更新的字段
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
        // 更新设备信息后清空记忆缓存并重新注册设备信息
        device = existingDevice;
        ChatSession session = null;
        if (device != null) {
            // Use ApplicationContext to get SessionManager to avoid circular dependency
            SessionManager sessionManager = applicationContext.getBean(SessionManager.class);
            session = sessionManager.getSessionByDeviceId(device.getDeviceId());
        }
        if (session != null) {
            session.setSysDevice(device);
        }
        return 1;
    }

    /**
     * 批量更新所有设备（用于项目启动时重置设备状态）
     *
     * @param device 设备信息（只使用 state 字段）
     * @return 更新的设备数量
     */
    @Transactional
    public int updateAllDevices(SysDevice device) {
        return sysDeviceRepository.updateAllStates(device.getState());
    }

}
