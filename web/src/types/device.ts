/**
 * 设备信息接口
 */
export interface Device {
  createTime?: string
  updateTime?: string
  roleId?: number
  avatar?: string
  roleName?: string
  roleDesc?: string
  voiceName?: string
  state?: string
  ttsId?: number
  modelId?: number
  modelName?: string
  sttId?: number
  temperature?: number
  topP?: number
  vadEnergyTh?: number
  vadSpeechTh?: number
  vadSilenceTh?: number
  vadSilenceMs?: number
  modelProvider?: string
  ttsProvider?: string
  isDefault?: string
  totalDevice?: number
  deviceId: string
  sessionId?: string
  deviceName?: string
  totalMessage?: number
  audioPath?: string
  lastLogin?: string
  wifiName?: string
  ip?: string
  chipModelName?: string
  type?: string
  version?: string
  functionNames?: string
  location?: string
  editable?: boolean // 表格编辑状态
}

import type { PageQueryParams } from './api'

/**
 * 设备查询参数
 */
export interface DeviceQueryParams extends PageQueryParams {
  deviceId?: string
  deviceName?: string
  roleName?: string
  state?: string | number
  // 重写 start 和 limit 为可选
  start?: number
  limit?: number
}

// 移除重复的响应类型定义，使用统一的 PageResponse<Device>

/**
 * 角色信息接口
 */
export interface Role {
  roleId: number
  roleName: string
  roleDesc?: string
  prompt?: string
  model?: string
  ttsVoice?: string
  createTime?: string
  updateTime?: string
}

