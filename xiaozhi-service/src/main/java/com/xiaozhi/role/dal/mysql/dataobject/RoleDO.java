package com.xiaozhi.role.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaozhi.common.model.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class RoleDO extends BaseDO {

    @TableId(value = "roleId", type = IdType.AUTO)
    private Integer roleId;

    private Integer userId;
    private String avatar;
    private String roleName;
    private String roleDesc;
    private String voiceName;
    private Float ttsPitch;
    private Float ttsSpeed;
    private String state;
    private Integer ttsId;
    private Integer modelId;
    private Integer sttId;
    private Double temperature;
    private Double topP;
    private Float vadEnergyTh;
    private Float vadSpeechTh;
    private Float vadSilenceTh;
    private Integer vadSilenceMs;
    private String isDefault;
    private String memoryType;
}
