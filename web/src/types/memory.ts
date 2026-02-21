/**
 * 记忆类型定义
 */

/**
 * 摘要记忆
 */
export interface SummaryMemory {
  deviceId: string
  roleId: number
  lastMessageTimestamp: string
  summary: string
  promptTokens: number
  completionTokens: number
  createTime: string
}

/**
 * 聊天消息（短期/窗口记忆）
 */
export interface ChatMemory {
  messageId: string
  deviceId: string
  roleId: number
  message: string
  sender: 'user' | 'assistant'
  createTime: string
  audioPath?: string
  messageType?: string
}

/**
 * 记忆查询参数
 */
export interface MemoryQueryParams {
  roleId: number
  deviceId: string
  start?: number
  limit?: number
}

/**
 * 记忆管理视图的状态
 */
export interface MemoryManagementState {
  roleId: number
  roleName: string
  memoryType: 'window' | 'summary'
  selectedDeviceId: string
  devices: Array<{
    deviceId: string
    deviceName: string
  }>
}
