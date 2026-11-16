package com.xiaozhi.common.model.bo;

import lombok.Data;

@Data
public class RoleBO {

    /*
     * 角色参数的默认值只在这里定义：service 的值对象补默认、读路径补 NULL 列、对话侧兜底、
     * 前端表单展示（经 /api/role/defaults）都引用它们，库表列默认与它们一致
     */
    public static final double DEFAULT_TEMPERATURE = 0.7d;
    public static final double DEFAULT_TOP_P = 0.9d;
    public static final double DEFAULT_TTS_PITCH = 1.0d;
    public static final double DEFAULT_TTS_SPEED = 1.0d;
    public static final float DEFAULT_VAD_SPEECH_TH = 0.5f;
    public static final float DEFAULT_VAD_SILENCE_TH = 0.3f;
    public static final float DEFAULT_VAD_ENERGY_TH = 0.01f;
    public static final int DEFAULT_VAD_SILENCE_MS = 800;
    public static final int DEFAULT_INACTIVE_TIMEOUT_SECONDS = 60;

    private Integer roleId;
    private Integer userId;
    private String avatar;
    private String roleName;
    private String roleDesc;
    private String voiceName;
    private Double ttsPitch = DEFAULT_TTS_PITCH;
    private Double ttsSpeed = DEFAULT_TTS_SPEED;
    private String state;
    private Integer ttsId;
    private Integer modelId;
    private Integer sttId;
    private String sttHotwords;
    private Double temperature = DEFAULT_TEMPERATURE;
    private Double topP = DEFAULT_TOP_P;
    private Float vadEnergyTh;
    private Float vadSpeechTh;
    private Float vadSilenceTh;
    private Integer vadSilenceMs;
    private Integer inactiveTimeoutSeconds = DEFAULT_INACTIVE_TIMEOUT_SECONDS;
    private String isDefault;
}
