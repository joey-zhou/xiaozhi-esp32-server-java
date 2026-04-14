package com.xiaozhi.authrole.service;

import com.xiaozhi.common.model.resp.AuthRolePermissionConfigResp;
import com.xiaozhi.common.model.resp.AuthRoleResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.model.resp.PermissionResp;

import java.util.List;

public interface AuthRoleService {

    PageResp<AuthRoleResp> page(int pageNo, int pageSize, String authRoleName, String roleKey, String status);

    AuthRoleResp get(Integer authRoleId);

    AuthRolePermissionConfigResp getPermissionConfig(Integer authRoleId);

    AuthRoleResp getByUserId(Integer userId);

    void assignPermissions(Integer authRoleId, List<Integer> permissionIds);

    List<PermissionResp> listPermissions(Integer authRoleId);
}
