/**
 * API 相关常量
 */

/**
 * 请求超时时间（毫秒）
 */
export const REQUEST_TIMEOUT = 30000

/**
 * 分页默认配置
 */
export const DEFAULT_PAGE_SIZE = 10
export const PAGE_SIZE_OPTIONS = [10, 30, 50, 100, 1000]

/**
 * 文件上传
 */
export const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB
export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
export const ALLOWED_AUDIO_TYPES = ['audio/mp3', 'audio/wav', 'audio/mpeg']

/**
 * 防抖延迟（毫秒）
 */
export const DEBOUNCE_DELAY = 500

/**
 * WebSocket 重连配置
 */
export const WS_RECONNECT_DELAY = 3000  // 重连延迟
export const WS_MAX_RECONNECT_TIMES = 5 // 最大重连次数

/**
 * 表单验证规则
 */
export const VALIDATION_RULES = {
  // 邮箱格式
  EMAIL_PATTERN: /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/,
  
  // 手机号格式（中国大陆）
  PHONE_PATTERN: /^1[3-9]\d{9}$/,
  
  // 密码强度（至少8位，包含字母和数字）
  PASSWORD_PATTERN: /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{8,}$/,
  
  // 用户名（4-20位字母、数字、下划线）
  USERNAME_PATTERN: /^[a-zA-Z0-9_]{4,20}$/,
}

/**
 * 密码长度限制
 */
export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 32

/**
 * 用户名长度限制
 */
export const USERNAME_MIN_LENGTH = 4
export const USERNAME_MAX_LENGTH = 20

/**
 * 设备名称长度限制
 */
export const DEVICE_NAME_MAX_LENGTH = 50

/**
 * 角色名称长度限制
 */
export const ROLE_NAME_MAX_LENGTH = 50

/**
 * 描述长度限制
 */
export const DESCRIPTION_MAX_LENGTH = 500

