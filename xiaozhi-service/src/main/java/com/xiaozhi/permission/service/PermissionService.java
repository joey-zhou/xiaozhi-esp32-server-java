package com.xiaozhi.permission.service;

import com.xiaozhi.common.model.bo.PermissionBO;

import java.util.List;

public interface PermissionService {

    String CACHE_NAME = "XiaoZhi:Permission";

    List<PermissionBO> listTree();

    List<PermissionBO> listByAuthRoleId(Integer authRoleId);

    List<Integer> listIdsByAuthRoleId(Integer authRoleId);

    void clearAuthRoleCache(Integer authRoleId);

    List<PermissionBO> listByUserId(Integer userId);

    List<PermissionBO> listTreeByUserId(Integer userId);

    /** 空白的 permissionKey 不会出现在结果里。 */
    List<String> listKeysByUserId(Integer userId);
}
