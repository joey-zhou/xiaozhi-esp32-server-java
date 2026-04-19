package com.xiaozhi.config.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaozhi.common.model.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class ConfigDO extends BaseDO {

    @TableId(value = "configId", type = IdType.AUTO)
    private Integer configId;

    private Integer userId;
    private String configName;
    private String configDesc;
    private String configType;
    private String modelType;
    private String provider;
    private String appId;
    private String apiKey;
    private String apiSecret;
    private String ak;
    private String sk;
    private String apiUrl;
    private String state;
    private String isDefault;
    private Boolean enableThinking;
}
