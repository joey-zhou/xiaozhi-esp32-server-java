package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dao.ConfigMapper;
import com.xiaozhi.dao.DeviceMapper;
import com.xiaozhi.dao.MessageMapper;
import com.xiaozhi.dao.RoleMapper;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysDeviceService;
import jakarta.annotation.Resource;
import org.apache.ibatis.javassist.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
    private DeviceMapper deviceMapper;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private ConfigMapper configMapper;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private SysConfigService configService;

    @Resource
    private ApplicationContext applicationContext;

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

        SysDevice existingDevice = deviceMapper.selectDeviceById(device.getDeviceId());
        if (existingDevice != null) {
            return 1;
        }

        // 查询是否有默认角色
        SysRole queryRole = new SysRole();
        queryRole.setUserId(device.getUserId());
        List<SysRole> roles = roleMapper.query(queryRole);

        if (roles.isEmpty()) {
            throw new NotFoundException("没有配置角色");
        }

        SysRole selectedRole = null;

        // 优先绑定默认角色
        for (SysRole role: roles) {
            if (("1").equals(role.getIsDefault())) {
                selectedRole = role;
                break;
            }
        }
        if  (selectedRole == null) {
            selectedRole = roles.getFirst();
        }

        device.setRoleId(selectedRole.getRoleId());
        return deviceMapper.add(device);

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
        int row = deviceMapper.delete(device);
        if (row > 0) {
            SysMessage message = new SysMessage();
            message.setUserId(device.getUserId());
            message.setDeviceId(device.getDeviceId());
            // 清空设备聊天记录
            messageMapper.delete(message);
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
        if(pageFilter != null){
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        return deviceMapper.query(device);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#deviceId.replace(\":\", \"-\")", unless = "#result == null")
    public SysDevice selectDeviceById(String deviceId) {
        return deviceMapper.selectDeviceById(deviceId);
    }

    /**
     * 查询验证码
     */
    @Override
    public SysDevice queryVerifyCode(SysDevice device) {
        return deviceMapper.queryVerifyCode(device);
    }

    /**
     * 查询并生成验证码
     * 
     */
    @Override
    public SysDevice generateCode(SysDevice device) {
        SysDevice result = deviceMapper.queryVerifyCode(device);
        if (result == null) {
            result = new SysDevice();
            deviceMapper.generateCode(device);
            result.setCode(device.getCode());
        }
        return result;
    }

    /**
     * 关系设备验证码语音路径
     */
    @Override
    public int updateCode(SysDevice device) {
        return deviceMapper.updateCode(device);
    }

    /**
     * 更新设备信息
     *
     * @param device
     * @return
     */
    @Override
    @CacheEvict(value = CACHE_NAME, key = "#device.deviceId.replace(\":\", \"-\")")
    public int update(SysDevice device) {
        int rows = deviceMapper.update(device);
        // 更新设备信息后清空记忆缓存并重新注册设备信息
        if(device.getDeviceId() != null){
            device = deviceMapper.selectDeviceById(device.getDeviceId());
        }
        ChatSession session = null;
        if (device != null) {
            // Use ApplicationContext to get SessionManager to avoid circular dependency
            SessionManager sessionManager = applicationContext.getBean(SessionManager.class);
            session = sessionManager.getSessionByDeviceId(device.getDeviceId());
        }
        if (session != null) {
            session.setSysDevice(device);
        }
        return rows;
    }

    @Override
    public String generateToken(String deviceId) {
        String token = UUID.randomUUID().toString();
        deviceMapper.insertCode(deviceId, token);
        return token;
    }

}