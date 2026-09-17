import { describe, expect, it } from 'vitest'

import * as storageKeys from '../storage'

/**
 * 真源是各 useStorage 调用点：store/user.ts、composables/useAuth.ts、
 * composables/useLocale.ts、composables/useAntdTheme.ts
 */
describe('存储键名登记表', () => {
  it('登记 store/user.ts 里全部 5 个键', () => {
    expect(storageKeys.STORAGE_USER_INFO).toBe('userInfo')
    expect(storageKeys.STORAGE_PERMISSIONS).toBe('permissions')
    expect(storageKeys.STORAGE_AUTH_ROLE).toBe('authRole')
    expect(storageKeys.STORAGE_USER_TOKEN).toBe('token')
    expect(storageKeys.STORAGE_WS_CONFIG).toBe('wsConfig')
  })

  it('登记 useAuth / useLocale / useAntdTheme 的键', () => {
    expect(storageKeys.STORAGE_USERNAME).toBe('username')
    expect(storageKeys.STORAGE_REMEMBER_ME).toBe('rememberMe')
    expect(storageKeys.STORAGE_LOCALE).toBe('locale')
    expect(storageKeys.STORAGE_THEME_MODE).toBe('theme-mode')
  })

  it('不登记任何没有写入方的键', () => {
    expect(Object.keys(storageKeys).filter((key) => key.startsWith('STORAGE_')).sort()).toEqual([
      'STORAGE_AUTH_ROLE',
      'STORAGE_LOCALE',
      'STORAGE_PERMISSIONS',
      'STORAGE_REMEMBER_ME',
      'STORAGE_THEME_MODE',
      'STORAGE_USERNAME',
      'STORAGE_USER_INFO',
      'STORAGE_USER_TOKEN',
      'STORAGE_WS_CONFIG',
    ])
  })

  it('键名两两不重复', () => {
    const values = Object.values(storageKeys)
    expect(new Set(values).size).toBe(values.length)
  })
})
