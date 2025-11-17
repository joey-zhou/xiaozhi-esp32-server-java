/**
 * 消息信息接口
 */
export interface Message {
  messageId: number
  deviceId: string
  deviceName?: string
  roleId?: number
  roleName?: string
  sender: 'user' | 'assistant'
  message: string
  audioPath?: string
  state: string
  messageType: string
  sessionId?: string
  createTime?: string
  updateTime?: string
  audioLoadError?: boolean
}

import type { PageQueryParams } from './api'

/**
 * 消息查询参数
 */
export interface MessageQueryParams extends PageQueryParams {
  deviceId?: string
  deviceName?: string
  sender?: string
  startTime?: string
  endTime?: string
  // 重写 start 和 limit 为可选
  start?: number
  limit?: number
}
