import type { PageQueryParams } from './api'

/**
 * 记忆类型定义
 */

/**
 * 对话摘要
 */
export interface SummaryMemory {
  id: number
  deviceId: string
  deviceName?: string | null
  roleId: number
  roleName?: string | null
  lastMessageTimestamp: string
  summary: string
  promptTokens: number
  completionTokens: number
  createTime: string
}

/**
 * 聊天消息（逐条对话记录）
 */
export interface ChatMemory {
  messageId: number
  deviceId: string
  roleId: number
  message: string
  // 工具调用回执的行 sender 是 tool，后端 MessageResp.sender 三种取值都会出现
  sender: 'user' | 'assistant' | 'tool'
  createTime: string
  audioPath?: string
  // NORMAL / TOOL_CALL / TOOL_RESPONSE，决定 toolCalls 里是哪一种负载
  messageType?: string
}

/**
 * 摘要查询参数：不传设备时查当前用户全部设备，不传角色时不限角色
 */
export interface SummaryQueryParams extends PageQueryParams {
  deviceId?: string
  roleId?: number
}
