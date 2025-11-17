package com.xiaozhi.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 用户角色配置（管理员、用户）
 *
 * @author Joey
 *
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysAuthRole extends Base<SysAuthRole> {
    private Integer roleId;
    private String roleName;
    private String roleKey;
    private String description;
    private String status;
}