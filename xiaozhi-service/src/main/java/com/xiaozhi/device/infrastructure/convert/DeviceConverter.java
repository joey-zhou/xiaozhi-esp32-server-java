package com.xiaozhi.device.infrastructure.convert;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import com.xiaozhi.device.domain.Device;
import com.xiaozhi.device.domain.vo.VerifyCode;
import org.springframework.stereotype.Component;

/**
 * Device 聚合根 ↔ DO / BO 转换器（领域层与基础设施层之间）。
 * <p>
 * 注意：此类负责 DO ↔ {@link Device} 聚合根的转换，
 * 与 MapStruct {@code DeviceConvert}（DO ↔ BO）职责不同，请勿混用。
 */
@Component
public class DeviceConverter {

    /** DeviceDO → Device 聚合根（从持久层重建） */
    public Device toDomain(DeviceDO d) {
        return new Device(
                d.getDeviceId(),
                d.getDeviceName(),
                d.getUserId(),
                d.getRoleId(),
                d.getMcpList(),
                d.getIp(),
                d.getLocation(),
                d.getWifiName(),
                d.getChipModelName(),
                d.getType(),
                d.getVersion(),
                d.getState(),
                d.getCreateTime(),
                d.getUpdateTime()
        );
    }

    /** Device 聚合根 → DeviceDO（写入持久层） */
    public DeviceDO toDataObject(Device device) {
        DeviceDO d = new DeviceDO();
        d.setDeviceId(device.getDeviceId());
        d.setDeviceName(device.getDeviceName());
        d.setUserId(device.getUserId());
        d.setRoleId(device.getRoleId());
        d.setMcpList(device.getMcpList());
        d.setIp(device.getIp());
        d.setLocation(device.getLocation());
        d.setWifiName(device.getWifiName());
        d.setChipModelName(device.getChipModelName());
        d.setType(device.getType());
        d.setVersion(device.getVersion());
        d.setState(device.getState());
        return d;
    }

    /** Device 聚合根 → DeviceBO（用于事件发布，sessionId / roleName 不可用） */
    public DeviceBO toBO(Device device) {
        DeviceBO bo = new DeviceBO();
        bo.setDeviceId(device.getDeviceId());
        bo.setDeviceName(device.getDeviceName());
        bo.setUserId(device.getUserId());
        bo.setRoleId(device.getRoleId());
        bo.setMcpList(device.getMcpList());
        bo.setIp(device.getIp());
        bo.setLocation(device.getLocation());
        bo.setWifiName(device.getWifiName());
        bo.setChipModelName(device.getChipModelName());
        bo.setType(device.getType());
        bo.setVersion(device.getVersion());
        bo.setState(device.getState());
        bo.setCreateTime(device.getCreateTime());
        bo.setUpdateTime(device.getUpdateTime());
        return bo;
    }

    /** VerifyCodeBO → VerifyCode 值对象 */
    public VerifyCode toVerifyCode(VerifyCodeBO bo) {
        return new VerifyCode(
                bo.getCode(),
                bo.getDeviceId(),
                bo.getSessionId(),
                bo.getType(),
                bo.getAudioPath(),
                bo.getCreateTime()
        );
    }
}
