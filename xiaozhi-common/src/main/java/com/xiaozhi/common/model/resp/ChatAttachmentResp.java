package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "网页聊天收下的附件")
public class ChatAttachmentResp {

    @Schema(description = "附件 ID，发消息时带上")
    private String attachmentId;

    @Schema(description = "附件种类: image")
    private String kind;

    @Schema(description = "原文件名")
    private String name;

    public static ChatAttachmentResp of(String attachmentId, String kind, String name) {
        ChatAttachmentResp resp = new ChatAttachmentResp();
        resp.setAttachmentId(attachmentId);
        resp.setKind(kind);
        resp.setName(name);
        return resp;
    }
}
