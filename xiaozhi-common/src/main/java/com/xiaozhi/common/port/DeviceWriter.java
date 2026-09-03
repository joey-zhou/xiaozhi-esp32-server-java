package com.xiaozhi.common.port;

import java.util.Set;

public interface DeviceWriter {

    /** 不加载聚合根、不发领域事件 */
    void updateState(String deviceId, String state);

    int batchUpdateState(Set<String> deviceIds, String state);

    void updateMcpList(String deviceId, String mcpList);

    /** 设备不存在时静默跳过 */
    void bindRole(String deviceId, Integer roleId);

    void register(String deviceId, String deviceName, String type, Integer userId, Integer roleId);
}
