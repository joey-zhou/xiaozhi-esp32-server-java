package com.xiaozhi.device.service;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.port.DeviceWriter;
import com.xiaozhi.device.model.DeviceProjection;

import java.util.List;

public interface DeviceService extends DeviceWriter {

    // ===================== 查询操作 =====================

    PageResult<DeviceProjection> page(int pageNo, int pageSize, String deviceId, String deviceName,
                                    String roleName, String state, Integer roleId, Integer userId);

    DeviceBO getBO(String deviceId);

    /** 按主键批量查询，供需要一次性拿一批设备状态的场景使用，避免逐台查询。 */
    List<DeviceBO> listByDeviceIds(List<String> deviceIds);

    List<DeviceBO> listByStateAndType(String state, String type);

    // ===================== 验证码操作（独立表，非 Device 聚合） =====================

    VerifyCodeBO generateCode(String deviceId, String sessionId, String type);

    int updateCodeAudioPath(String deviceId, String sessionId, String code, String audioPath);

}
