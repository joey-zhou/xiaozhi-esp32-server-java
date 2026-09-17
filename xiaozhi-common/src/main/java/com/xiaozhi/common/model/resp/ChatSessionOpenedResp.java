package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "开启 Web 聊天会话的结果")
public class ChatSessionOpenedResp {

    @Schema(description = "会话 ID，后续流式聊天与关闭会话都用它")
    private String sessionId;

    public static ChatSessionOpenedResp of(String sessionId) {
        ChatSessionOpenedResp resp = new ChatSessionOpenedResp();
        resp.setSessionId(sessionId);
        return resp;
    }
}
