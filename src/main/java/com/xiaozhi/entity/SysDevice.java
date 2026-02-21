package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 设备表
 * 
 * @author Joey
 * 
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({ "startTime", "endTime", "start", "limit", "code" })
@Schema(description = "设备信息")
public class SysDevice extends SysRole {
    public static final String DEVICE_STATE_STANDBY = "2";//已在线，未激活对话
    public static final String DEVICE_STATE_ONLINE = "1";//已在线，已激活对话
    public static final String DEVICE_STATE_OFFLINE = "0";

    @Schema(description = "设备唯一标识ID", example = "ESP32_001")
    private String deviceId;

    @Schema(description = "当前会话ID", example = "session_123456")
    private String sessionId;

    /**
     * 设备名称
     */
    @Schema(description = "设备名称/别名", example = "客厅小智音箱")
    private String deviceName;

    /**
     * 设备状态
     */
    @Schema(description = "设备状态：0-离线，1-在线已激活对话，2-在线未激活对话", example = "1", allowableValues = {"0", "1", "2"})
    private String state;

    /**
     * 设备对话次数
     */
    @Schema(description = "设备累计对话次数", example = "50")
    private Integer totalMessage;

    /**
     * 验证码
     */
    @Schema(description = "设备配对验证码", example = "ABCD1234")
    private String code;

    /**
     * 音频文件
     */
    @Schema(description = "设备音频文件路径", example = "/audio/device_001.wav")
    private String audioPath;

    /**
     * 最后在线时间
     * device表的 lastLogin字段,目前没有特别的用处。 如果需要知道设备的最近在线时间，也可以通过访问conversation表最后一条记录获知。
     * 所以计划在后续版本合适的时候舍弃此字段。
     */
    @Deprecated
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最后在线时间（已废弃）", example = "2024-12-01 10:30:00", deprecated = true)
    private String lastLogin;

    /**
     * WiFi名称
     */
    @Schema(description = "设备连接的WiFi网络名称", example = "MyHome-WiFi")
    private String wifiName;

    /**
     * IP
     */
    @Schema(description = "设备IP地址", example = "192.168.1.101")
    private String ip;

    /**
     * 芯片型号
     */
    @Schema(description = "芯片型号名称", example = "ESP32-S3")
    private String chipModelName;

    /**
     * 芯片类型
     */
    @Schema(description = "芯片类型", example = "ESP32")
    private String type;

    /**
     * 固件版本
     */
    @Schema(description = "设备固件版本号", example = "v3.0.0")
    private String version;

    /**
     * 可用全局function的名称列表(逗号分割)，为空则使用所有全局function
     */
    @Schema(description = "可用的全局function名称列表，逗号分隔。为空表示使用所有", example = "weather,time,alarm")
    private String functionNames;

    /**
     * 地理位置
     */
    @Schema(description = "设备所在地理位置", example = "北京市朝阳区")
    private String location;

}