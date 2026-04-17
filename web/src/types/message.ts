import type { PageQueryParams } from './api'

/**
 * 消息发送方
 */
export type MessageSender = 'user' | 'assistant' | 'system'

/**
 * 消息信息接口（对齐后端 MessageResp）
 */
export interface Message {
  messageId: number
  deviceId: string
  deviceName?: string
  sender: MessageSender
  message: string
  audioPath?: string
  state?: string
  messageType?: string
  toolCalls?: string
  sessionId?: string
  /** 消息来源: 'web' | 'device' */
  source?: string
  roleId?: number
  roleName?: string
  createTime?: string
  updateTime?: string
  // 前端扩展字段（非后端返回）
  audioLoadError?: boolean
}

/**
 * 会话信息接口（对齐后端 ConversationResp）
 */
export interface Conversation {
  sessionId: string
  roleId: number
  roleName: string
  title: string
  updateTime: string
}

/**
 * 消息查询参数（对齐后端 MessagePageReq）
 */
export interface MessageQueryParams extends PageQueryParams {
  deviceId?: string
  deviceName?: string
  sender?: string
  messageType?: string
  roleId?: number
  startTime?: string
  endTime?: string
  sessionId?: string
  /** 消息来源过滤: 'web' | 'device' */
  source?: string
}

/**
 * 会话查询参数（对齐后端 ConversationPageReq）
 */
export interface ConversationQueryParams extends PageQueryParams {
  roleId?: number
  /** 消息来源过滤: 'web' | 'device' */
  source?: string
}
