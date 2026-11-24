import type { PageQueryParams } from './api'

/**
 * 消息发送方
 */
export type MessageSender = 'user' | 'assistant' | 'tool'

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
  /** 随消息发来的附件 */
  attachments?: MessageAttachment[]
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
 * 消息附件（对齐后端 MessageAttachmentResp）
 */
export interface MessageAttachment {
  kind: 'image'
  name: string
  /** 签过名的访问地址，经 getResourceUrl 拼成完整地址 */
  url: string
}
