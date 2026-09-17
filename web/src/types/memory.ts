import type { PageQueryParams } from './api'

/**
 * 记忆类型定义
 */

/**
 * 摘要记忆
 */
export interface SummaryMemory {
  id: number
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
 * 记忆查询参数
 */
export interface MemoryQueryParams extends PageQueryParams {
  roleId: number
  deviceId: string
}
