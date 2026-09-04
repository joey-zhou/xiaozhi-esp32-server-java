package com.xiaozhi.authrole.service;

import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.bo.AuthRoleBO;

import java.util.List;

public interface AuthRoleService {

    PageResult<AuthRoleBO> page(int pageNo, int pageSize, String authRoleName, String roleKey, String status);

    /** 不存在时返回 null。 */
    AuthRoleBO getBO(Integer authRoleId);

    /** 不存在时返回 null。 */
    String getRoleKey(Integer authRoleId);

    void assignPermissions(Integer authRoleId, List<Integer> permissionIds);
}
