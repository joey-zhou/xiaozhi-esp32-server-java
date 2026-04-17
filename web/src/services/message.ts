import { http } from './request'
import api from './api'
import type {
  Message,
  Conversation,
  MessageQueryParams,
  ConversationQueryParams,
} from '@/types/message'

export type { Message, Conversation, MessageQueryParams, ConversationQueryParams }

/**
 * 查询消息列表
 */
export function queryMessages(params: MessageQueryParams) {
  return http.getPage<Message>(api.message.query, params)
}

/**
 * 删除消息
 */
export function deleteMessage(messageId: number) {
  return http.delete(`${api.message.delete}/${messageId}`)
}

/**
 * 批量删除设备消息
 */
export function batchDeleteMessages(deviceId: string) {
  return http.delete(api.message.delete, { deviceId })
}

/**
 * 导出消息
 */
export function exportMessages(params: Omit<MessageQueryParams, 'pageNo' | 'pageSize'>) {
  return http.get(api.message.export, params)
}

/**
 * 查询会话列表
 */
export function queryConversations(params: ConversationQueryParams) {
  return http.getPage<Conversation>(api.message.conversations, params)
}
