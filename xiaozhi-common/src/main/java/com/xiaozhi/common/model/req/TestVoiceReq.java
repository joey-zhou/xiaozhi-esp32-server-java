package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "测试语音合成请求")
public class TestVoiceReq {

    @Schema(description = "消息文本", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "消息文本不能为空")
    private String message;

    @Schema(description = "语音合成提供方", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "提供方不能为空")
    private String provider;

    @Schema(description = "TTS配置ID")
    private Integer ttsId;

    @Schema(description = "音色名称")
    private String voiceName;

    @Schema(description = "语音音调(0.5-2.0)")
    @DecimalMin(value = "0.5", message = "语音音调不能小于0.5")
    @DecimalMax(value = "2.0", message = "语音音调不能大于2.0")
    private double ttsPitch = 1.0;

    @Schema(description = "语音语速(0.5-2.0)")
    @DecimalMin(value = "0.5", message = "语音语速不能小于0.5")
    @DecimalMax(value = "2.0", message = "语音语速不能大于2.0")
    private double ttsSpeed = 1.0;
}
