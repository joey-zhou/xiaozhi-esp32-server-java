package com.xiaozhi.role.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaozhi.common.dal.mysql.dataobject.BaseDO;
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
    private Double ttsPitch;
    private Double ttsSpeed;
    private String state;
    // 切回本地 TTS / STT 时这两列要写成 NULL；默认策略会跳过 null 字段，切回去等于没改
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer ttsId;
    private Integer modelId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer sttId;
    private Double temperature;
    private Double topP;
    private Float vadEnergyTh;
    private Float vadSpeechTh;
    private Float vadSilenceTh;
    private Integer vadSilenceMs;
    private Integer inactiveTimeoutSeconds;
    private String isDefault;
}
