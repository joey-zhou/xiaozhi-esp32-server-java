package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.GenericGenerator;

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
@Entity
@Table(name = "sys_device")
@DynamicUpdate
@DynamicInsert
public class SysDevice extends Base<SysDevice> {
    public static final String DEVICE_STATE_STANDBY = "2";//已在线，未激活对话
    public static final String DEVICE_STATE_ONLINE = "1";//已在线，已激活对话
    public static final String DEVICE_STATE_OFFLINE = "0";

    /**
     * 设备 ID，主键
     */
    @Id
    @Column(length = 255)
    @Schema(description = "设备唯一标识ID", example = "ESP32_001")
    @GeneratedValue(generator = "assigned")
    @GenericGenerator(name = "assigned", strategy = "assigned")
    private String deviceId;

    @Schema(description = "当前会话ID", example = "session_123456")
    @Transient
    private String sessionId;

    /**
     * 设备名称
     */
    @Column(nullable = false, length = 100)
    @Schema(description = "设备名称/别名", example = "客厅小智音箱")
    private String deviceName;

    /**
     * 角色 ID
     * 设备状态
     */
    @Schema(description = "角色 ID")
    private Integer roleId;
    /**
     * 设备状态
     */
    @Schema(description = "设备状态：0-离线，1-在线已激活对话，2-在线未激活对话", example = "1", allowableValues = {"0", "1", "2"})
    private String state;
    /**
     * 可用全局 function 的名称列表
     * 设备对话次数
     */
    @Column(length = 250)
    @Schema(description = "可用的全局 function 名称列表，逗号分隔", example = "weather,time,alarm")
    private String functionNames;
    /**
     * 设备对话次数
     */
    @Schema(description = "设备累计对话次数", example = "50")
    private Integer totalMessage;

    /**
     * 验证码
     */
    /**
     * IP 地址
     * 验证码
     */
    @Column(length = 45)
    @Schema(description = "设备 IP 地址", example = "192.168.1.101")
    private String ip;
    /**
     * 验证码
     */
    @Schema(description = "设备配对验证码", example = "ABCD1234")
    private String code;
    /**
     * 地理位置
     */
    @Column(length = 255)
    @Schema(description = "设备所在地理位置", example = "北京市朝阳区")
    private String location;
    /**
     * 音频文件
     */
    @Schema(description = "设备音频文件路径", example = "/audio/device_001.wav")
    @Column(length = 255)
    private String audioPath;

    /**
     * WiFi名称
     */
    @Schema(description = "设备连接的WiFi网络名称", example = "MyHome-WiFi")
    @Column(length = 100)
    private String wifiName;



    /**
     * 芯片型号
     */
    @Column(length = 100)
    @Schema(description = "芯片型号名称", example = "ESP32-S3")
    private String chipModelName;

    /**
     * 芯片类型
     */
    @Column(length = 50)
    @Schema(description = "设备类型", example = "ESP32")
    private String type;

    /**
     * 固件版本
     */
    @Column(length = 50)
    @Schema(description = "设备固件版本号", example = "v3.0.0")
    private String version;





}