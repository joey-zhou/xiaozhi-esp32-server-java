package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "关闭 Web 聊天会话的结果")
public class ChatSessionClosedResp {

    /** 取值固定为 closed，前端按它判定会话已释放 */
    private static final String STATUS_CLOSED = "closed";

    @Schema(description = "会话状态", allowableValues = {"closed"})
    private String status;

    public static ChatSessionClosedResp closed() {
        ChatSessionClosedResp resp = new ChatSessionClosedResp();
        resp.setStatus(STATUS_CLOSED);
        return resp;
    }
}
