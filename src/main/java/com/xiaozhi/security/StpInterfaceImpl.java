package com.xiaozhi.security;

import cn.dev33.satoken.stp.StpInterface;
import com.xiaozhi.entity.SysAuthRole;
import com.xiaozhi.entity.SysPermission;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysAuthRoleService;
import com.xiaozhi.service.SysPermissionService;
import com.xiaozhi.service.SysUserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token权限接口实现
 * 用于Sa-Token框架获取用户的权限和角色信息
 *
 * @author Joey
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private SysPermissionService permissionService;

    @Resource
    private SysUserService userService;

    @Resource
    private SysAuthRoleService authRoleService;

    /**
     * 返回用户的权限列表
     *
     * @param loginId 用户ID
     * @param loginType 登录类型
     * @return 权限列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> permissionList = new ArrayList<>();

        try {
            Integer userId = Integer.parseInt(loginId.toString());

            // 查询用户权限
            List<SysPermission> permissions = permissionService.selectByUserId(userId);

            // 提取权限key
            for (SysPermission permission : permissions) {
                if (permission.getPermissionKey() != null && !permission.getPermissionKey().isEmpty()) {
                    permissionList.add(permission.getPermissionKey());
                }
            }
        } catch (Exception e) {
            // 记录日志但不抛出异常,返回空列表
            System.err.println("获取用户权限失败: " + e.getMessage());
        }

        return permissionList;
    }

    /**
     * 返回用户的角色列表
     *
     * @param loginId 用户ID
     * @param loginType 登录类型
     * @return 角色列表
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> roleList = new ArrayList<>();

        try {
            Integer userId = Integer.parseInt(loginId.toString());

            // 查询用户信息
            SysUser user = userService.selectUserByUserId(userId);

            if (user != null && user.getRoleId() != null) {
                // 查询角色信息
                SysAuthRole role = authRoleService.selectById(user.getRoleId());

                if (role != null && role.getRoleKey() != null && !role.getRoleKey().isEmpty()) {
                    roleList.add(role.getRoleKey());
                }

                // 如果是超级管理员,添加admin角色
                if ("1".equals(user.getIsAdmin())) {
                    roleList.add("admin");
                }
            }
        } catch (Exception e) {
            // 记录日志但不抛出异常,返回空列表
            System.err.println("获取用户角色失败: " + e.getMessage());
        }

        return roleList;
    }
}
