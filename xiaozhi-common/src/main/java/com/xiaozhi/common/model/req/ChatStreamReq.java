package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流式聊天请求。
 * 用户输入必须走请求体，放 query 会被 access log 与反向代理日志留存。
 */
@Data
@Schema(description = "流式聊天请求")
public class ChatStreamReq {

    @NotBlank
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;

    @NotBlank
    @Schema(description = "用户输入文本", requiredMode = Schema.RequiredMode.REQUIRED)
    private String text;
}
