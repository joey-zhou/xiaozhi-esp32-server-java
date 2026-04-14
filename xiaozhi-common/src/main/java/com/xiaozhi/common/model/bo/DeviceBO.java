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
    private String sessionId;
    private String deviceName;
    private Integer roleId;
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
