package com.xiaozhi.verifycode.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** sys_code 没有 updateTime，不继承 BaseDO。 */
@Data
@TableName("sys_code")
public class VerifyCodeDO {

    @TableId(value = "codeId", type = IdType.AUTO)
    private Integer codeId;

    private String code;
    private String type;

    /** 收码账号：邮箱或手机号，两种渠道共用一列；设备码这一半为 NULL */
    private String account;

    private String deviceId;
    private String sessionId;
    private String audioPath;
    private LocalDateTime createTime;
}
