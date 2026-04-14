package com.xiaozhi.permission.service;

import com.xiaozhi.common.model.resp.PermissionResp;
import com.xiaozhi.common.model.resp.PermissionTreeResp;

import java.util.List;

public interface PermissionService {

    String CACHE_NAME = "XiaoZhi:Permission";

    List<PermissionTreeResp> listTree();

    List<PermissionResp> listByAuthRoleId(Integer authRoleId);

    List<Integer> listIdsByAuthRoleId(Integer authRoleId);

    void clearAuthRoleCache(Integer authRoleId);

    List<PermissionResp> listByUserId(Integer userId);

    List<PermissionTreeResp> listTreeByUserId(Integer userId);
}
