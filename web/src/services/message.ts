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
  return http.getPage<Message>(api.message.root, params)
}

/**
 * 删除消息
 */
export function deleteMessage(messageId: number) {
  return http.delete(`${api.message.root}/${messageId}`)
}

/**
 * 查询会话列表
 */
export function queryConversations(params: ConversationQueryParams) {
  return http.getPage<Conversation>(api.message.conversations, params)
}
