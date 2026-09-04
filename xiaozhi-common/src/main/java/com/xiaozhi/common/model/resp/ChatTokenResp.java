package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "流式聊天的一个 Token")
public class ChatTokenResp {

    @Schema(description = "类型", allowableValues = {"thinking", "content"})
    private String type;

    @Schema(description = "文本内容")
    private String text;
}
