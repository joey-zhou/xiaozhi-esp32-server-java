package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 流式聊天请求。
 * 用户输入必须走请求体，放 query 会被 access log 与反向代理日志留存。
 */
@Data
@Schema(description = "流式聊天请求")
public class ChatStreamReq {

    /** 一句话最多带几个附件 */
    public static final int MAX_ATTACHMENTS = 4;

    @NotBlank
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;

    @NotBlank
    @Schema(description = "用户输入文本", requiredMode = Schema.RequiredMode.REQUIRED)
    private String text;

    @Size(max = MAX_ATTACHMENTS)
    @Schema(description = "先经上传附件接口收下的附件 ID")
    private List<String> attachmentIds;
}
