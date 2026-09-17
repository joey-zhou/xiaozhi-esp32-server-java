/**
 * API 相关常量
 * 取值以后端校验注解、库表列定义与现行前端实现为准，改动前须回到对应真源核对
 */

/**
 * 请求超时时间（毫秒），services/request.ts 的 axios timeout 直接取这个值
 */
export const REQUEST_TIMEOUT = 30000

/**
 * 后端判定操作有不可逆后果、要求二次确认时的业务码
 * 真源 ResultStatus.java CONFIRM_REQUIRED；响应走 HTTP 200，message 是给用户看的后果说明
 */
export const CODE_CONFIRM_REQUIRED = 4090

/**
 * 分页默认配置
 * 对应 composables/useTable.ts 的分页初值
 */
export const DEFAULT_PAGE_SIZE = 10
/** antd TablePaginationConfig.pageSizeOptions 只接受字符串数组 */
export const PAGE_SIZE_OPTIONS = ['10', '30', '50', '100', '1000']

/**
 * 文件上传大小上限（字节）
 * 对应 utils/fileValidators.ts 里 fileValidators 的四档阈值，取值为不含上界
 */
export const MAX_IMAGE_SIZE = 2 * 1024 * 1024
export const MAX_AUDIO_SIZE = 10 * 1024 * 1024

export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
export const ALLOWED_AUDIO_TYPES = ['audio/mp3', 'audio/wav', 'audio/mpeg']

/**
 * 防抖延迟（毫秒）
 */
export const DEBOUNCE_DELAY = 500

/**
 * WebSocket 重连配置
 * 对应 services/websocket.ts 的 reconnectDelay / maxReconnectAttempts
 */
export const WS_RECONNECT_DELAY = 2000  // 首次重连延迟，实际按 1.5 倍退避
export const WS_MAX_RECONNECT_TIMES = 5 // 最大重连次数

/**
 * 表单验证规则
 */
export const VALIDATION_RULES = {
  // 邮箱格式，后端为 @Email
  EMAIL_PATTERN: /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/,

  // 手机号格式，与 UserRegisterReq.tel 的 @Pattern 一致
  PHONE_PATTERN: /^1[3-9]\d{9}$/,

  // 密码强度：长度区间取自 UserRegisterReq.password 的 @Size(6,20)，字母+数字为前端附加要求
  PASSWORD_PATTERN: /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{6,20}$/,

  // 用户名：长度区间取自 UserRegisterReq.username 的 @Size(3,20)
  USERNAME_PATTERN: /^[a-zA-Z0-9_]{3,20}$/,
}

/**
 * 密码长度限制
 * 真源 UserRegisterReq.java:24 @Size(min = 6, max = 20)
 */
export const PASSWORD_MIN_LENGTH = 6
export const PASSWORD_MAX_LENGTH = 20

/**
 * 用户名长度限制
 * 真源 UserRegisterReq.java:19 @Size(min = 3, max = 20)
 */
export const USERNAME_MIN_LENGTH = 3
export const USERNAME_MAX_LENGTH = 20

/**
 * 设备名称长度限制
 * 真源 V1__init.sql sys_device.deviceName varchar(100)
 */
export const DEVICE_NAME_MAX_LENGTH = 100

/**
 * 角色名称长度限制
 * 真源 V1__init.sql sys_role.roleName varchar(100)
 */
export const ROLE_NAME_MAX_LENGTH = 100

/**
 * 描述长度限制
 * 真源 V1__init.sql sys_template.templateDesc varchar(500)
 */
export const DESCRIPTION_MAX_LENGTH = 500
