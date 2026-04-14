package com.xiaozhi.device.service;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.common.model.resp.DeviceResp;
import com.xiaozhi.common.model.resp.PageResp;

import java.util.List;

public interface DeviceService {

    /** 设备缓存名称（DeviceServiceImpl 读缓存、DeviceRepositoryImpl 写后失效均使用此常量） */
    String CACHE_NAME = "XiaoZhi:Device";

    // ===================== 查询操作 =====================

    PageResp<DeviceResp> page(int pageNo, int pageSize, String deviceId, String deviceName,
                              String roleName, String state, Integer roleId, Integer userId);

    DeviceBO getBO(String deviceId);

    List<DeviceBO> listByStateAndType(String state, String type);

    DeviceResp get(String deviceId);

    // ===================== 验证码操作（独立表，非 Device 聚合） =====================

    VerifyCodeBO generateCode(String deviceId, String sessionId, String type);

    int updateCodeAudioPath(String deviceId, String sessionId, String code, String audioPath);

}
