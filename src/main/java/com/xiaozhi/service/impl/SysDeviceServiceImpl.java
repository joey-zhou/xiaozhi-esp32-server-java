package com.xiaozhi.service.impl;

import java.util.List;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.dao.DeviceMapper;
import com.xiaozhi.entity.SysDevice;
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

    /**
     * 添加设备
     *
     * @param device
     * @return
     */
    @Override
    @Transactional
    public int add(SysDevice device) {
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
        return deviceMapper.delete(device);
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