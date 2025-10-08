import { ref } from 'vue'
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
}

export interface WebSocketConfig {
  url: string
  deviceId?: string
  deviceName?: string
  autoConnect: boolean
}

export const useUserStore = defineStore('user', () => {
  const userInfo = useStorage<UserInfo | null>('userInfo', null, localStorage, {
    serializer: {
      read: (v: any) => {
        try {
          return v ? JSON.parse(v) : null
        } catch (e) {
          console.error('Failed to parse user info:', e)
          return null
        }
      },
      write: (v: any) => JSON.stringify(v),
    },
  })

  // Token 管理
  const token = useStorage<string>('token', '', localStorage)

  // WebSocket 配置管理
  const wsConfig = useStorage<WebSocketConfig>(
    'wsConfig',
    {
      url: 'ws://127.0.0.1:8091/ws/xiaozhi/v1/',
      deviceId: 'web_test',
      deviceName: 'Web用户',
      autoConnect: true
    },
    localStorage,
    {
      serializer: {
        read: (v: any) => {
          try {
            return v ? JSON.parse(v) : {
              url: 'ws://127.0.0.1:8091/ws/xiaozhi/v1/',
              deviceId: 'web_test',
              deviceName: 'Web用户',
              autoConnect: true
            }
          } catch (e) {
            return {
              url: 'ws://127.0.0.1:8091/ws/xiaozhi/v1/',
              deviceId: 'web_test',
              deviceName: 'Web用户',
              autoConnect: true
            }
          }
        },
        write: (v: any) => JSON.stringify(v),
      },
    }
  )

  const navigationStyle = useStorage<'tabs' | 'sidebar'>('navigationStyle', 'tabs', localStorage)
  const isMobile = ref(false)

  const setUserInfo = (info: UserInfo) => {
    userInfo.value = info
  }

  const setMobileType = (mobile: boolean) => {
    isMobile.value = mobile
  }

  const setNavigationStyle = (style: 'tabs' | 'sidebar') => {
    navigationStyle.value = style
  }

  const clearUserInfo = () => {
    userInfo.value = null
  }

  const updateUserInfo = (info: Partial<UserInfo>) => {
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

  const updateWsConfig = (config: Partial<WebSocketConfig>) => {
    wsConfig.value = { ...wsConfig.value, ...config }
  }

  return {
    userInfo,
    token,
    wsConfig,
    isMobile,
    navigationStyle,
    setUserInfo,
    setMobileType,
    setNavigationStyle,
    clearUserInfo,
    updateUserInfo,
    setToken,
    clearToken,
    updateWsConfig,
  }
})