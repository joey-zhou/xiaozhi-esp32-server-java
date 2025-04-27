package com.xiaozhi.service.impl;

import java.util.List;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.dao.ConfigMapper;
import com.xiaozhi.dao.DeviceMapper;
import com.xiaozhi.dao.MessageMapper;
import com.xiaozhi.dao.RoleMapper;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysDeviceService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 设备操作
 *
 * @author Joey
 *
 */

@Service
public class SysDeviceServiceImpl implements SysDeviceService {

    @Resource
    private DeviceMapper deviceMapper;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private ConfigMapper configMapper;

    @Resource
    private RoleMapper roleMapper;

    /**
     * 添加设备
     *
     * @param device
     * @return
     */
    @Override
    @Transactional
    public int add(SysDevice device) {
        // 先查询是否有默认配置
        SysConfig queryConfig = new SysConfig();
        queryConfig.setUserId(device.getUserId());
        queryConfig.setIsDefault("1");
        List<SysConfig> configs = configMapper.query(queryConfig);
        // 查询是否有默认角色
        SysRole queryRole = new SysRole();
        queryRole.setUserId(device.getUserId());
        queryRole.setIsDefault("1");
        SysRole role = roleMapper.query(queryRole).get(0);
        // 遍历查询是否有默认配置
        for (SysConfig config : configs) {
            if (config.getConfigType().equals("llm")) {
                device.setModelId(config.getConfigId());
            } else if (config.getConfigType().equals("stt")) {
                device.setSttId(config.getConfigId());
            }
        }
        if (role != null) {
            device.setRoleId(role.getRoleId());
        }
        // 添加设备
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
    public List<SysDevice> query(SysDevice device) {
        if (device.getLimit() != null && device.getLimit() > 0) {
            PageHelper.startPage(device.getStart(), device.getLimit());
        }
        return deviceMapper.query(device);
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
    @Transactional
    public int update(SysDevice device) {
        return deviceMapper.update(device);
    }

}