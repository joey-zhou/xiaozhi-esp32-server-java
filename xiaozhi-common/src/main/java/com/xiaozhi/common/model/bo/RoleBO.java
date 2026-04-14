package com.xiaozhi.common.model.bo;

import lombok.Data;

@Data
public class RoleBO {

    private Integer roleId;
    private Integer userId;
    private String avatar;
    private String roleName;
    private String roleDesc;
    private String voiceName;
    private Float ttsPitch = 1.0f;
    private Float ttsSpeed = 1.0f;
    private String state;
    private Integer ttsId;
    private Integer modelId;
    private Integer sttId;
    private Double temperature = 0.7d;
    private Double topP = 0.9d;
    private Float vadEnergyTh;
    private Float vadSpeechTh;
    private Float vadSilenceTh;
    private Integer vadSilenceMs;
    private String isDefault;
    private String memoryType;
}
