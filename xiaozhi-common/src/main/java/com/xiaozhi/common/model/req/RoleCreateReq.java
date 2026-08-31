package com.xiaozhi.common.model.req;

import com.xiaozhi.common.annotation.SignedFileUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "创建角色")
public class RoleCreateReq {

    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    @Schema(description = "角色描述")
    private String roleDesc;

    @Schema(description = "角色头像")
    @SignedFileUrl
    private String avatar;

    @Schema(description = "语音名称")
    private String voiceName;

    @Schema(description = "语音音调")
    private Double ttsPitch;

    @Schema(description = "语音语速")
    private Double ttsSpeed;

    @Schema(description = "状态(1启用 0禁用)")
    private String state;

    @Schema(description = "TTS服务ID")
    private Integer ttsId;

    @Schema(description = "模型ID")
    private Integer modelId;

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

    @Schema(description = "会话空闲自动结束秒数，0表示关闭")
    @Min(value = 0, message = "会话空闲时长不能小于0秒")
    @Max(value = 3600, message = "会话空闲时长不能超过3600秒")
    private Integer inactiveTimeoutSeconds;

    @Schema(description = "是否默认角色(1是 0否)")
    private String isDefault;

    @Schema(description = "记忆类型")
    private String memoryType;
}
