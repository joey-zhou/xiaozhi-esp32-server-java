package com.xiaozhi.role.service;

import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.role.model.RoleProjection;

import java.util.List;

public interface RoleService {

    /** 角色缓存名称（RoleServiceImpl 读缓存、RoleRepositoryImpl 写后失效均使用此常量） */
    String CACHE_NAME = "XiaoZhi:Role";

    // ===================== 查询操作 =====================

    PageResult<RoleProjection> page(int pageNo, int pageSize, Integer roleId, String roleName,
                                    String isDefault, String state, Integer userId);

    RoleBO getBO(Integer roleId);

    List<RoleBO> listBO(Integer userId, int limit);

    RoleBO getDefaultOrFirstBO(Integer userId);

    // ===================== 写操作（待迁移到 RoleAppService） =====================

    Integer copyDefaultRole(Integer sourceUserId, Integer targetUserId);
}
