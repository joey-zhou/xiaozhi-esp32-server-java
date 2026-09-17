package com.xiaozhi.role.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 角色分页结果集，字段名与 RoleMapper.xml 的列别名逐字一致。 */
@Data
public class RoleProjection {

    private Integer roleId;
    private String avatar;
    private String roleName;
    private String roleDesc;
    private String voiceName;
    private Double ttsPitch;
    private Double ttsSpeed;
    private String state;
    private Integer ttsId;
    private Integer modelId;
    private String modelName;
    private Integer sttId;
    private Double temperature;
    private Double topP;
    private Float vadEnergyTh;
    private Float vadSpeechTh;
    private Float vadSilenceTh;
    private Integer vadSilenceMs;
    private Integer inactiveTimeoutSeconds;
    private String modelProvider;
    private String ttsProvider;
    private String isDefault;
    private Integer totalDevice;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
