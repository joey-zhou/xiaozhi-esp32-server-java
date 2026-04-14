package com.xiaozhi.common.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "角色信息")
public class RoleResp {

    @Schema(description = "角色ID")
    private Integer roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色描述")
    private String roleDesc;

    @Schema(description = "角色头像")
    private String avatar;

    @Schema(description = "语音名称")
    private String voiceName;

    @Schema(description = "语音音调")
    private Float ttsPitch;

    @Schema(description = "语音语速")
    private Float ttsSpeed;

    @Schema(description = "状态(1启用 0禁用)")
    private String state;

    @Schema(description = "TTS服务ID")
    private Integer ttsId;

    @Schema(description = "模型ID")
    private Integer modelId;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "STT服务ID")
    private Integer sttId;

    @Schema(description = "温度参数")
    private Double temperature;

    @Schema(description = "Top-P参数")
    private Double topP;

    @Schema(description = "语音活动检测-能量阈值")
    private Float vadEnergyTh;

    @Schema(description = "语音活动检测-语音阈值")
    private Float vadSpeechTh;

    @Schema(description = "语音活动检测-静音阈值")
    private Float vadSilenceTh;

    @Schema(description = "语音活动检测-静音毫秒数")
    private Integer vadSilenceMs;

    @Schema(description = "模型提供商")
    private String modelProvider;

    @Schema(description = "TTS服务提供商")
    private String ttsProvider;

    @Schema(description = "是否默认角色(1是 0否)")
    private String isDefault;

    @Schema(description = "总设备数")
    private Integer totalDevice;

    @Schema(description = "记忆类型")
    private String memoryType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
