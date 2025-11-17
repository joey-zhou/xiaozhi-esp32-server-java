/**
 * Store 统一导出
 * 便于集中管理和导入
 */
export { useUserStore } from './user'
export { useLoadingStore } from './loading'
export { useAppStore } from './app'
export { useDeviceStore } from './device'
export { usePermissionStore } from './permission'

// 导出类型
export type { UserInfo, WebSocketConfig } from './user'
export type { Locale } from './app'
export type { Permission } from './permission'

