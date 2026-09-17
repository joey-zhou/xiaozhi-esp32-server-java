/**
 * 后端枚举值登记表
 * 只登记「后端真实下发、前端要拿来比较」的取值，取值以库表列定义与 Resp 类为准。
 * 纯前端概念（主题、语言、上传状态等）由各自的 composable 定义类型，不在这里再登记一份。
 */

/**
 * 用户状态
 * 真源 UserResp.java state 为 String 与 V1__init.sql sys_user.state enum('1','0')，为字符串
 */
export enum UserState {
  DISABLED = '0', // 禁用
  NORMAL = '1',   // 正常
}

/**
 * 用户类型
 * 真源 UserResp.java isAdmin 为 String 与 V1__init.sql sys_user.isAdmin enum('1','0')，为字符串
 */
export enum UserType {
  NORMAL = '0',   // 普通用户
  ADMIN = '1',    // 管理员
}

/**
 * 设备状态
 * 真源 DeviceBO.java:11-15 与 V1__init.sql sys_device.state enum('0','1','2')，为字符串
 */
export enum DeviceState {
  OFFLINE = '0',  // 离线
  ONLINE = '1',   // 在线
  STANDBY = '2',  // 待机
}

/**
 * 配置类型
 * 真源 types/config.ts 的 ConfigType 联合类型
 */
export enum ConfigType {
  LLM = 'llm',                  // 大语言模型
  STT = 'stt',                  // 语音识别
  TTS = 'tts',                  // 语音合成
  AGENT = 'agent',              // 智能体
  VOICE_CLONE = 'voice_clone',  // 声音克隆
  OSS = 'oss',                  // 对象存储
}
