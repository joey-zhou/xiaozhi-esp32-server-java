package com.xiaozhi.common.model.resp;

import com.xiaozhi.common.annotation.SignedFileUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "消息附件")
public class MessageAttachmentResp {

    @Schema(description = "附件种类: image")
    private String kind;

    @Schema(description = "原文件名")
    private String name;

    @Schema(description = "访问地址")
    @SignedFileUrl
    private String url;
}
