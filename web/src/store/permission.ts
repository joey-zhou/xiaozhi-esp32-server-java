import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useUserStore } from './user'
import type { RouteRecordRaw } from 'vue-router'

/**
 * 权限类型
 */
export type Permission = string

/**
 * 权限状态 Store
 * 管理用户权限、动态路由等
 */
export const usePermissionStore = defineStore('permission', () => {
  const userStore = useUserStore()

  // ========== 权限列表 ==========
  const permissions = ref<Permission[]>([])

  // ========== 路由权限 ==========
  const accessedRoutes = ref<RouteRecordRaw[]>([])

  // ========== 计算属性 ==========
  
  // 使用全局的 isAdmin
  const { isAdmin } = userStore

  /**
   * 是否有指定权限
   */
  const hasPermission = (permission: Permission): boolean => {
    // 管理员拥有所有权限
    if (isAdmin) {
      return true
    }
    return permissions.value.includes(permission)
  }

  /**
   * 是否有任一权限
   */
  const hasAnyPermission = (perms: Permission[]): boolean => {
    if (isAdmin) {
      return true
    }
    return perms.some(perm => permissions.value.includes(perm))
  }

  /**
   * 是否有所有权限
   */
  const hasAllPermissions = (perms: Permission[]): boolean => {
    if (isAdmin) {
      return true
    }
    return perms.every(perm => permissions.value.includes(perm))
  }

  // ========== 操作方法 ==========

  /**
   * 设置权限列表
   */
  const setPermissions = (perms: Permission[]) => {
    permissions.value = perms
  }

  /**
   * 添加权限
   */
  const addPermission = (permission: Permission) => {
    if (!permissions.value.includes(permission)) {
      permissions.value.push(permission)
    }
  }

  /**
   * 移除权限
   */
  const removePermission = (permission: Permission) => {
    const index = permissions.value.indexOf(permission)
    if (index !== -1) {
      permissions.value.splice(index, 1)
    }
  }

  /**
   * 设置可访问的路由
   */
  const setAccessedRoutes = (routes: RouteRecordRaw[]) => {
    accessedRoutes.value = routes
  }

  /**
   * 过滤路由（根据权限）
   */
  const filterRoutes = (routes: RouteRecordRaw[]): RouteRecordRaw[] => {
    const filteredRoutes: RouteRecordRaw[] = []

    routes.forEach(route => {
      const tmp = { ...route }
      
      // 检查是否需要管理员权限
      if (tmp.meta?.isAdmin && !isAdmin) {
        return
      }

      // 递归过滤子路由
      if (tmp.children) {
        tmp.children = filterRoutes(tmp.children)
      }

      filteredRoutes.push(tmp)
    })

    return filteredRoutes
  }

  /**
   * 清空权限
   */
  const clearPermissions = () => {
    permissions.value = []
    accessedRoutes.value = []
  }

  return {
    // 状态
    permissions,
    accessedRoutes,
    isAdmin,
    
    // 方法
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    setPermissions,
    addPermission,
    removePermission,
    setAccessedRoutes,
    filterRoutes,
    clearPermissions,
  }
})

