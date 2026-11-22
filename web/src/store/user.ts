import { computed } from 'vue'
import { defineStore } from 'pinia'
import { useStorage } from '@vueuse/core'
import type { AuthRole, PermissionTreeNode } from '@/types/authRole'
import type { User } from '@/types/user'
import {
  STORAGE_AUTH_ROLE,
  STORAGE_PERMISSIONS,
  STORAGE_USER_INFO,
  STORAGE_USER_TOKEN,
} from '@/constants/storage'

function createJsonSerializer<T>(fallback: T, label: string) {
  return {
    read: (raw: string): T => {
      try {
        return raw ? (JSON.parse(raw) as T) : fallback
      } catch (e) {
        console.error(`Failed to parse ${label}:`, e)
        return fallback
      }
    },
    write: (value: T) => JSON.stringify(value),
  }
}

export const useUserStore = defineStore('user', () => {
  const userInfo = useStorage<User | null>(STORAGE_USER_INFO, null, localStorage, {
    serializer: createJsonSerializer<User | null>(null, 'user info'),
  })

  // 权限信息
  const permissions = useStorage<PermissionTreeNode[]>(STORAGE_PERMISSIONS, [], localStorage, {
    serializer: createJsonSerializer<PermissionTreeNode[]>([], 'permissions'),
  })

  // 后台权限角色信息
  const authRole = useStorage<AuthRole | null>(STORAGE_AUTH_ROLE, null, localStorage, {
    serializer: createJsonSerializer<AuthRole | null>(null, 'auth role'),
  })

  // Token 管理
  const token = useStorage<string>(STORAGE_USER_TOKEN, '', localStorage)

  const setUserInfo = (info: User) => {
    userInfo.value = info
  }

  const setPermissions = (perms: PermissionTreeNode[]) => {
    permissions.value = perms
  }

  const setAuthRole = (roleInfo: AuthRole) => {
    authRole.value = roleInfo
  }

  const clearUserInfo = () => {
    userInfo.value = null
    permissions.value = []
    authRole.value = null
  }

  const updateUserInfo = (info: Partial<User>) => {
    if (userInfo.value) {
      userInfo.value = { ...userInfo.value, ...info }
    }
  }

  const setToken = (newToken: string) => {
    token.value = newToken
  }

  const clearToken = () => {
    token.value = ''
  }

  // 计算属性 - 是否为管理员
  const isAdmin = computed(() => userInfo.value?.isAdmin === '1')

  // 权限检查方法

  // 后端返回的是树形权限（菜单 root，其 children 挂 button/api 权限）。
  // 将整棵树拍平成 Set，供 hasPermission 递归匹配（否则深层 button/api 权限查不到）。
  const permissionKeySet = computed(() => {
    const keys = new Set<string>()
    const walk = (list?: PermissionTreeNode[]) => {
      if (!list) return
      for (const perm of list) {
        if (perm.permissionKey) keys.add(perm.permissionKey)
        if (perm.children?.length) walk(perm.children)
      }
    }
    walk(permissions.value)
    return keys
  })

  // 管理员的全权由后端给出（下发全部权限键），这里不按身份放行，与接口鉴权同一口径
  const hasPermission = (permissionKey: string): boolean => permissionKeySet.value.has(permissionKey)

  const hasAnyPermission = (permissionKeys: string[]): boolean =>
    permissionKeys.some(key => hasPermission(key))

  const hasAllPermissions = (permissionKeys: string[]): boolean =>
    permissionKeys.every(key => hasPermission(key))

  return {
    userInfo,
    permissions,
    authRole,
    token,
    isAdmin,
    setUserInfo,
    setPermissions,
    setAuthRole,
    clearUserInfo,
    updateUserInfo,
    setToken,
    clearToken,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
  }
})
