package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "会话重命名")
public class ConversationRenameReq {

    @Schema(description = "新标题")
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最多 100 个字")
    private String title;
}
