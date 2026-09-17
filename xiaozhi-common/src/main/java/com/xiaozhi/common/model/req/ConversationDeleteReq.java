package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量删除会话")
public class ConversationDeleteReq {

    @Schema(description = "要删除的会话ID")
    @NotEmpty(message = "请选择要删除的会话")
    @Size(max = 100, message = "一次最多删除 100 个会话")
    private List<@NotBlank String> sessionIds;
}
