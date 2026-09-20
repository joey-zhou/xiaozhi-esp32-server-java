package com.xiaozhi.userauth.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaozhi.common.dal.mysql.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_auth")
public class UserAuthDO extends BaseDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer userId;
    private String openId;
    private String unionId;
    private String platform;
    private String profile;
}
