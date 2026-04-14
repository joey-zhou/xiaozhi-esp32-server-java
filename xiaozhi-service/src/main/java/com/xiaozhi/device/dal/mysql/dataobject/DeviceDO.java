package com.xiaozhi.device.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaozhi.common.model.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_device")
public class DeviceDO extends BaseDO {

    @TableId(value = "deviceId", type = IdType.INPUT)
    private String deviceId;

    private String deviceName;
    private Integer roleId;
    private String mcpList;
    private String ip;
    private String location;
    private String wifiName;
    private String chipModelName;
    private String type;
    private String version;
    private String state;
    private Integer userId;
}
