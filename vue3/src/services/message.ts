import { http } from './request'
import api from './api'
import type { Message, MessageQueryParams, MessageListResponse } from '@/types/message'

/**
 * 查询消息列表
 */
export function queryMessages(params: MessageQueryParams) {
  return http.get<MessageListResponse>(api.message.query, params)
}

/**
 * 删除消息
 */
export function deleteMessage(messageId: number | string) {
  return http.post(api.message.delete, { messageId })
}

/**
 * 更新消息
 */
export function updateMessage(data: Partial<Message>) {
  return http.post(api.message.update, data)
}

