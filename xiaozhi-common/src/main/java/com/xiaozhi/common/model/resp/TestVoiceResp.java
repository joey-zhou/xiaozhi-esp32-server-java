package com.xiaozhi.common.model.resp;

import com.xiaozhi.common.annotation.SignedFileUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "测试语音合成结果")
public class TestVoiceResp {

    /**
     * 试听音频路径。
     * <p>
     * 本地存储为相对路径（前端拼接后端地址访问），云存储为完整 URL，
     * 经 {@code FileUrlSigningResponseBodyAdvice} 在响应写出前按当前存储服务自动签名。
     */
    @Schema(description = "试听音频访问地址")
    @SignedFileUrl
    private String audioUrl;

    public TestVoiceResp() {
    }

    public TestVoiceResp(String audioUrl) {
        this.audioUrl = audioUrl;
    }
}
