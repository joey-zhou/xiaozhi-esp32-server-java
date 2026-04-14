package com.xiaozhi.permission.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaozhi.common.model.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class PermissionDO extends BaseDO {

    @TableId(value = "permissionId", type = IdType.AUTO)
    private Integer permissionId;

    private Integer parentId;
    private String name;
    private String permissionKey;
    private String permissionType;
    private String path;
    private String component;
    private String icon;
    private Integer sort;
    private String visible;
    private String status;
}
