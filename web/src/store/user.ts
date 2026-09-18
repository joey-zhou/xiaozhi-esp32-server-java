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
  STORAGE_WS_CONFIG,
} from '@/constants/storage'

export interface WebSocketConfig {
  url: string
  deviceName?: string
}

// 设备 WebSocket 由对话进程（xiaozhi-dialogue）提供，端口与 API 进程不同；
// 路径尾斜杠是后端 AntPathMatcher 的硬要求，services/websocket.ts 连接前会补齐
const WS_PATH = '/ws/xiaozhi/v1/'

// 未配置 VITE_WS_URL 时按当前页面地址推导，由前置的 nginx/vite 代理转给 dialogue。
// 同一份构建产物因此能在任意 IP、域名、端口下直接用，换地址不必重新打包；
// 页面是 https 时自动用 wss，否则浏览器会以 Mixed Content 拦截明文连接
function resolveDefaultWsUrl(): string {
  if (typeof window === 'undefined') {
    return `ws://localhost:8092${WS_PATH}`
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}${WS_PATH}`
}

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

  // WebSocket 配置管理
  const defaultWsConfig: WebSocketConfig = {
    url: import.meta.env.VITE_WS_URL || resolveDefaultWsUrl(),
  }

  const wsConfig = useStorage<WebSocketConfig>(
    STORAGE_WS_CONFIG,
    defaultWsConfig,
    localStorage,
    {
      serializer: createJsonSerializer<WebSocketConfig>(defaultWsConfig, 'ws config'),
    }
  )

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

  const hasPermission = (permissionKey: string): boolean => {
    // 管理员拥有所有权限
    if (isAdmin.value) {
      return true
    }
    return permissionKeySet.value.has(permissionKey)
  }

  const hasAnyPermission = (permissionKeys: string[]): boolean => {
    if (isAdmin.value) {
      return true
    }
    return permissionKeys.some(key => hasPermission(key))
  }

  const hasAllPermissions = (permissionKeys: string[]): boolean => {
    if (isAdmin.value) {
      return true
    }
    return permissionKeys.every(key => hasPermission(key))
  }

  const updateWsConfig = (config: Partial<WebSocketConfig>) => {
    wsConfig.value = { ...wsConfig.value, ...config }
  }

  return {
    userInfo,
    permissions,
    authRole,
    token,
    wsConfig,
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
    updateWsConfig,
  }
})
