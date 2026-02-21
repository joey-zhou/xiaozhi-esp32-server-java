package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 删除消息请求参数
 *
 * @author Joey
 */
@Data
@Schema(description = "删除消息请求参数")
public class MessageDeleteParam {

    @Schema(description = "消息ID（传递此参数会删除单条消息）", example = "1")
    private Integer messageId;

    @Schema(description = "设备ID（传递此参数会删除该设备所有消息）", example = "ESP32_001")
    private String deviceId;
}
