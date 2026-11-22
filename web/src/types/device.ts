import type { PageQueryParams } from './api'

/**
 * 设备信息，字段对应后端 DeviceResp
 */
export interface Device {
  lastLogin?: string
  deviceId: string
  sessionId?: string
  deviceName?: string
  roleId?: number
  roleName?: string
  state?: string
  code?: string
  audioPath?: string
  wifiName?: string
  ip?: string
  chipModelName?: string
  type?: string
  version?: string
  mcpList?: string
  location?: string
  createTime?: string
  updateTime?: string
  editable?: boolean // 表格编辑状态
}

/** 批量改智能体的结果，对应后端 DeviceBatchUpdateResp */
export interface DeviceBatchUpdateResult {
  successCount: number
  totalCount: number
}

/**
 * 设备查询参数
 */
export interface DeviceQueryParams extends PageQueryParams {
  deviceId?: string
  deviceName?: string
  roleName?: string
  state?: string | number
}


