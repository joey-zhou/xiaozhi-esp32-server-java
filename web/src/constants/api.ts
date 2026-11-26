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
 * 后端未单独映射的异常走的兜底业务码（HTTP 500），文案固定、不是写给用户的原文
 * 真源 ResultStatus.java UNEXPECTED_ERROR；前端遇到它改用调用方自己的失败提示
 */
export const CODE_UNEXPECTED_ERROR = 5000

/**
 * 分页默认配置
 * 对应 composables/useTable.ts 的分页初值
 */
export const DEFAULT_PAGE_SIZE = 10
/** antd TablePaginationConfig.pageSizeOptions 只接受字符串数组 */
export const PAGE_SIZE_OPTIONS = ['10', '30', '50', '100', '1000']

/**
 * 文件上传大小上限（字节），取值为不含上界
 * 真源 UploadPolicy.java Category 的 AVATAR、AUDIO，UploadLimitsContractTest 读这里核对
 */
export const MAX_IMAGE_SIZE = 2 * 1024 * 1024
export const MAX_AUDIO_SIZE = 10 * 1024 * 1024

/**
 * 图片 / 音频文件上传的 MIME 白名单
 * 供 utils/fileValidators.ts 的 image / audio 校验器使用；浏览器给不出这些 MIME 时
 * 校验器会回退按扩展名判定，两者是「或」的关系，不会互相顶替
 */
export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
export const ALLOWED_AUDIO_TYPES = ['audio/mp3', 'audio/wav', 'audio/mpeg']

/**
 * 网页聊天一句话最多带几个附件
 * 真源 ChatStreamReq.java MAX_ATTACHMENTS
 */
export const CHAT_ATTACHMENT_MAX_COUNT = 4

/**
 * 网页聊天发图前缩到的最长边，并统一转成 JPEG/PNG，低于后端上限
 * 真源 ImageUploadPolicy.java MAX_BYTES（5MB）、MAX_DIMENSION（4096）
 */
export const CHAT_IMAGE_MAX_EDGE = 2048
export const CHAT_IMAGE_MAX_BYTES = 5 * 1024 * 1024

/**
 * 网页聊天流两次数据之间的最长间隔，超时中断整条流；须大于后端工具执行上限加模型静默超时
 * 真源 XiaoZhiToolCallingManager.java TOOL_CALL_TIMEOUT（120s）、application-common.yml xiaozhi.llm.idle-timeout-seconds（90s）
 */
export const CHAT_STREAM_IDLE_TIMEOUT_MS = 240000

/**
 * 音色试听会调第三方合成（计费），要有角色的新建或修改权限
 * 真源 RoleController.java testAudio 的 @SaCheckPermission
 */
export const VOICE_PREVIEW_PERMISSIONS = ['system:role:api:create', 'system:role:api:update']

/**
 * 防抖延迟（毫秒）
 */
export const DEBOUNCE_DELAY = 500

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
