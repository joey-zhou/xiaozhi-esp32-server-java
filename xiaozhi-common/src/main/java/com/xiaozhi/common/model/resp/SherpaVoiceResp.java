package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "本地 sherpa-onnx 音色")
public class SherpaVoiceResp {

    @Schema(description = "展示名称")
    private String label;

    @Schema(description = "音色取值（modelDir:modelType:speakerId）")
    private String value;

    @Schema(description = "服务提供商，固定为 sherpa-onnx")
    private String provider;

    @Schema(description = "模型目录名")
    private String model;
}
