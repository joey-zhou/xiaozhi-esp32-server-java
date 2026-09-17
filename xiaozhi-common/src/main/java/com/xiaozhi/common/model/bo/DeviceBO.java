package com.xiaozhi.common.model.bo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceBO {

    /** @see com.xiaozhi.device.domain.Device#STATE_STANDBY */
    public static final String DEVICE_STATE_STANDBY = "2";
    /** @see com.xiaozhi.device.domain.Device#STATE_ONLINE */
    public static final String DEVICE_STATE_ONLINE = "1";
    /** @see com.xiaozhi.device.domain.Device#STATE_OFFLINE */
    public static final String DEVICE_STATE_OFFLINE = "0";

    private String deviceId;

    /**
     * 当前连接的会话 ID，仅在 dialogue 运行期（设备在线、已建立 WebSocket 会话）才有值；
     * 由 {@code DeviceService.getBO} 等从 DO 转换得到的 DeviceBO 里恒为 null，不要用它判断设备是否在线。
     */
    private String sessionId;
    private String deviceName;
    private Integer roleId;

    /**
     * 当前会话绑定的角色名，仅在 dialogue 运行期（随会话切换角色后）才有值；
     * 由 {@code DeviceService.getBO} 等从 DO 转换得到的 DeviceBO 里恒为 null，需要角色名请查 roleId 对应的 RoleBO。
     */
    private String roleName;
    private String state;
    private Integer userId;
    private String mcpList;
    private String location;
    private String ip;
    private String wifiName;
    private String chipModelName;
    private String type;
    private String version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
