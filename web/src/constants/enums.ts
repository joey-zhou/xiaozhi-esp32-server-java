/**
 * 后端枚举值登记表
 * 只登记「后端真实下发、前端要拿来比较」的取值，取值以库表列定义与 Resp 类为准。
 * 纯前端概念（主题、语言、上传状态等）由各自的 composable 定义类型，不在这里再登记一份。
 */

/**
 * 设备状态
 * 真源 DeviceBO.java:11-15 与 V1__init.sql sys_device.state enum('0','1','2')，为字符串
 */
export enum DeviceState {
  OFFLINE = '0',  // 离线
  ONLINE = '1',   // 在线
  STANDBY = '2',  // 待机
}
