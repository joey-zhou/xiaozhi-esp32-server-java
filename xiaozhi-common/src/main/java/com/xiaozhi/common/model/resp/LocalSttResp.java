package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "服务端本地语音识别的当前状态")
public class LocalSttResp {

    @Schema(description = "当前加载的本地 provider：sherpa-onnx / vosk，模型都未就位时为 null")
    private String provider;

    @Schema(description = "本地识别是否可用")
    private boolean available;
}
