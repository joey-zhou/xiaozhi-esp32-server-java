/**
 * 枚举常量定义
 * 用于替代魔法数字，提高代码可读性和维护性
 */

/**
 * HTTP 状态码
 */
export enum HttpStatus {
  SUCCESS = 200,
  UNAUTHORIZED = 401,
  FORBIDDEN = 403,
  NOT_FOUND = 404,
  SERVER_ERROR = 500,
}

/**
 * API 响应码
 */
export enum ApiCode {
  SUCCESS = 200,
  ERROR = 500,
  UNAUTHORIZED = 401,
  FORBIDDEN = 403,
}

/**
 * 用户状态
 */
export enum UserState {
  DISABLED = 0, // 禁用
  NORMAL = 1,   // 正常
}

/**
 * 用户类型
 */
export enum UserType {
  NORMAL = 0,   // 普通用户
  ADMIN = 1,    // 管理员
}

/**
 * 设备状态
 */
export enum DeviceState {
  OFFLINE = 0,  // 离线
  ONLINE = 1,   // 在线
}

/**
 * 消息类型
 */
export enum MessageType {
  TEXT = 'text',
  AUDIO = 'audio',
  IMAGE = 'image',
  SYSTEM = 'system',
}

/**
 * 消息发送者类型
 */
export enum SenderType {
  USER = 'user',
  ASSISTANT = 'assistant',
  SYSTEM = 'system',
}

/**
 * WebSocket 状态
 */
export enum WebSocketState {
  CONNECTING = 0, // 连接中
  OPEN = 1,       // 已连接
  CLOSING = 2,    // 关闭中
  CLOSED = 3,     // 已关闭
}

/**
 * 主题模式
 */
export enum ThemeMode {
  LIGHT = 'light',
  DARK = 'dark',
  AUTO = 'auto',
}

/**
 * 语言
 */
export enum Locale {
  ZH_CN = 'zh-CN',
  EN_US = 'en-US',
}

/**
 * 导航风格
 */
export enum NavigationStyle {
  SIDEBAR = 'sidebar',
  TABS = 'tabs',
}

/**
 * 表格操作类型
 */
export enum TableAction {
  ADD = 'add',
  EDIT = 'edit',
  DELETE = 'delete',
  VIEW = 'view',
}

/**
 * 文件上传状态
 */
export enum UploadStatus {
  READY = 'ready',
  UPLOADING = 'uploading',
  SUCCESS = 'success',
  ERROR = 'error',
}

/**
 * 角色类型
 */
export enum RoleType {
  CUSTOM = 'custom',
  SYSTEM = 'system',
}

/**
 * 配置类型
 */
export enum ConfigType {
  LLM = 'llm',           // 大语言模型
  STT = 'stt',           // 语音识别
  TTS = 'tts',           // 语音合成
  AGENT = 'agent',       // 智能体
}

/**
 * 成功/失败标识
 */
export const SUCCESS = true
export const FAILURE = false

/**
 * 是/否标识
 */
export const YES = 1
export const NO = 0

