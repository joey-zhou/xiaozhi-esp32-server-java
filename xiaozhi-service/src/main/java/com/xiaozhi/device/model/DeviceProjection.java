package com.xiaozhi.device.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 设备列表与详情结果集，字段名与 DeviceMapper.xml 的列别名逐字一致。 */
@Data
public class DeviceProjection {

    private String deviceId;
    private String deviceName;
    private Integer roleId;
    private String roleName;
    private String state;
    private Integer totalMessage;
    private String wifiName;
    private String ip;
    private String chipModelName;
    private String type;
    private String version;
    private String location;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
