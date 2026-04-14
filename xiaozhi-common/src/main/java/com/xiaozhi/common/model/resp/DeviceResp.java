package com.xiaozhi.common.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "设备信息")
public class DeviceResp {

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "当前会话ID")
    private String sessionId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "角色ID")
    private Integer roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "设备状态")
    private String state;

    @Schema(description = "消息总数")
    private Integer totalMessage;

    @Schema(description = "验证码")
    private String code;

    @Schema(description = "音频路径")
    private String audioPath;

    @Schema(description = "WiFi 名称")
    private String wifiName;

    @Schema(description = "IP")
    private String ip;

    @Schema(description = "芯片型号")
    private String chipModelName;

    @Schema(description = "设备类型")
    private String type;

    @Schema(description = "固件版本")
    private String version;

    @Schema(description = "设备 MCP 能力列表")
    private String mcpList;

    @Schema(description = "地理位置")
    private String location;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
