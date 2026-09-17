/**
 * LocalStorage / SessionStorage 键名常量
 * 统一管理存储键名，避免字符串硬编码
 * 只登记代码里真实写入过的键，新增前须先确认对应 useStorage 调用点
 */

/**
 * 用户相关
 */
export const STORAGE_USER_INFO = 'userInfo'
export const STORAGE_USER_TOKEN = 'token'
export const STORAGE_PERMISSIONS = 'permissions'
export const STORAGE_AUTH_ROLE = 'authRole'
export const STORAGE_USERNAME = 'username'
export const STORAGE_REMEMBER_ME = 'rememberMe'

/**
 * 主题相关
 */
export const STORAGE_THEME_MODE = 'theme-mode'

/**
 * 语言相关
 */
export const STORAGE_LOCALE = 'locale'

/**
 * WebSocket 相关
 */
export const STORAGE_WS_CONFIG = 'wsConfig'
