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
    private String email;
    private String deviceId;
    private String sessionId;
    private String audioPath;
    private LocalDateTime createTime;
}
