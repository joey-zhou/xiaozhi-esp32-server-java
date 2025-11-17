import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useStorage } from '@vueuse/core'

export interface UserInfo {
  userId?: string
  username?: string
  email?: string
  name?: string
  tel?: string
  avatar?: string
  state?: string // 1-正常 0-禁用
  isAdmin?: string // 1-管理员 0-普通用户
  totalDevice?: number
  aliveNumber?: number
  totalMessage?: number
  loginTime?: string
  loginIp?: string
  roleId?: number
}

// 权限信息
export interface Permission {
  permissionId: number
  parentId?: number
  name: string
  permissionKey: string
  permissionType: 'menu' | 'button' | 'api'
  path?: string
  component?: string
  icon?: string
  sort?: number
  visible?: string // '1'-显示 '0'-隐藏
  status?: string // '1'-启用 '0'-禁用
  children?: Permission[]
}

// 角色信息
export interface Role {
  roleId: number
  roleName: string
  roleKey: string
  description?: string
  status?: string
  createTime?: string
  updateTime?: string
}

// 登录响应数据
export interface LoginResponse {
  user: UserInfo
  role: Role
  permissions: Permission[]
  token: string
  refreshToken: string
  sessionId: string
}

export interface WebSocketConfig {
  url: string
  deviceName?: string
}

export const useUserStore = defineStore('user', () => {
  const userInfo = useStorage<UserInfo | null>('userInfo', null, localStorage, {
    serializer: {
      read: (v: string) => {
        try {
          return v ? JSON.parse(v) as UserInfo : null
        } catch (e) {
          console.error('Failed to parse user info:', e)
          return null
        }
      },
      write: (v: UserInfo | null) => JSON.stringify(v),
    },
  })

  // 权限信息
  const permissions = useStorage<Permission[]>('permissions', [], localStorage, {
    serializer: {
      read: (v: any) => {
        try {
          return v ? JSON.parse(v) : []
        } catch (e) {
          console.error('Failed to parse permissions:', e)
          return []
        }
      },
      write: (v: any) => JSON.stringify(v),
    },
  })

  // 角色信息
  const role = useStorage<Role | null>('role', null, localStorage, {
    serializer: {
      read: (v: any) => {
        try {
          return v ? JSON.parse(v) : null
        } catch (e) {
          console.error('Failed to parse role:', e)
          return null
        }
      },
      write: (v: any) => JSON.stringify(v),
    },
  })

  // Token 管理
  const token = useStorage<string>('token', '', localStorage)
  const refreshToken = useStorage<string>('refreshToken', '', localStorage)

  // WebSocket 配置管理
  const defaultWsConfig: WebSocketConfig = {
    url: import.meta.env.VITE_WS_URL || 'ws://localhost:8091/ws/xiaozhi/v1',
  }
  
  const wsConfig = useStorage<WebSocketConfig>(
    'wsConfig',
    defaultWsConfig,
    localStorage,
    {
      serializer: {
        read: (v: string) => {
          try {
            return v ? JSON.parse(v) as WebSocketConfig : defaultWsConfig
          } catch (e) {
            return defaultWsConfig
          }
        },
        write: (v: WebSocketConfig) => JSON.stringify(v),
      },
    }
  )

  const navigationStyle = useStorage<'tabs' | 'sidebar'>('navigationStyle', 'tabs', localStorage)
  const isMobile = ref(false)

  const setUserInfo = (info: UserInfo) => {
    userInfo.value = info
  }

  const setPermissions = (perms: Permission[]) => {
    permissions.value = perms
  }

  const setRole = (roleInfo: Role) => {
    role.value = roleInfo
  }

  const setMobileType = (mobile: boolean) => {
    isMobile.value = mobile
  }

  const setNavigationStyle = (style: 'tabs' | 'sidebar') => {
    navigationStyle.value = style
  }

  const clearUserInfo = () => {
    userInfo.value = null
    permissions.value = []
    role.value = null
  }

  const updateUserInfo = (info: Partial<UserInfo>) => {
    if (userInfo.value) {
      userInfo.value = { ...userInfo.value, ...info }
    }
  }

  const setToken = (newToken: string) => {
    token.value = newToken
  }

  const setRefreshToken = (newRefreshToken: string) => {
    refreshToken.value = newRefreshToken
  }

  const clearToken = () => {
    token.value = ''
    refreshToken.value = ''
  }

  // 计算属性 - 是否为管理员
  const isAdmin = computed(() => userInfo.value?.isAdmin == '1')

  // 权限检查方法
  const hasPermission = (permissionKey: string): boolean => {
    // 管理员拥有所有权限
    if (isAdmin.value) {
      return true
    }
    return permissions.value.some(perm => perm.permissionKey === permissionKey)
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
    role,
    token,
    refreshToken,
    wsConfig,
    isMobile,
    navigationStyle,
    isAdmin,
    setUserInfo,
    setPermissions,
    setRole,
    setMobileType,
    setNavigationStyle,
    clearUserInfo,
    updateUserInfo,
    setToken,
    setRefreshToken,
    clearToken,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    updateWsConfig,
  }
})