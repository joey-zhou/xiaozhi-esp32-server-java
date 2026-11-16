package com.xiaozhi.common.model.resp;

import com.xiaozhi.common.model.bo.RoleBO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色参数的默认值，取自 {@link RoleBO}。前端新建表单用它展示，用户没改过的参数不必提交，后端按同一份默认补齐。
 */
@Schema(description = "角色参数默认值")
public record RoleDefaultsResp(
    @Schema(description = "采样温度") double temperature,
    @Schema(description = "核采样 topP") double topP,
    @Schema(description = "语音检测阈值") float vadSpeechTh,
    @Schema(description = "静音检测阈值") float vadSilenceTh,
    @Schema(description = "能量检测阈值") float vadEnergyTh,
    @Schema(description = "静音判定时长（毫秒）") int vadSilenceMs,
    @Schema(description = "无人说话自动结束的秒数") int inactiveTimeoutSeconds,
    @Schema(description = "音调") double ttsPitch,
    @Schema(description = "语速") double ttsSpeed) {

    public static RoleDefaultsResp current() {
        return new RoleDefaultsResp(RoleBO.DEFAULT_TEMPERATURE, RoleBO.DEFAULT_TOP_P,
            RoleBO.DEFAULT_VAD_SPEECH_TH, RoleBO.DEFAULT_VAD_SILENCE_TH, RoleBO.DEFAULT_VAD_ENERGY_TH,
            RoleBO.DEFAULT_VAD_SILENCE_MS, RoleBO.DEFAULT_INACTIVE_TIMEOUT_SECONDS,
            RoleBO.DEFAULT_TTS_PITCH, RoleBO.DEFAULT_TTS_SPEED);
    }
}
