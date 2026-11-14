package com.xiaozhi.permission.service;

import com.xiaozhi.common.model.bo.PermissionBO;

import java.util.List;

public interface PermissionService {

    List<PermissionBO> listTree();

    List<PermissionBO> listByAuthRoleId(Integer authRoleId);

    List<Integer> listIdsByAuthRoleId(Integer authRoleId);

    /** 只读 permissionKey 列，不经过 BO 转换；空白的 permissionKey 不会出现在结果里。 */
    List<String> listKeysByAuthRoleId(Integer authRoleId);

    void clearAuthRoleCache(Integer authRoleId);

    /** 全部启用权限的 key；空白的 permissionKey 不会出现在结果里。 */
    List<String> listAllKeys();

    /** 管理员拿全部启用权限，不看后台角色分到了哪些 */
    List<PermissionBO> listByUserId(Integer userId);

    List<PermissionBO> listTreeByUserId(Integer userId);

    /** 管理员拿全部启用权限的 key；空白的 permissionKey 不会出现在结果里。 */
    List<String> listKeysByUserId(Integer userId);
}
